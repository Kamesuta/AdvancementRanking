package com.kamesuta.advrank.display;

import com.kamesuta.advrank.data.PlayerData;
import com.kamesuta.advrank.utils.NMSUtils;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;

import static com.kamesuta.advrank.AdvRankingPlugin.app;

/**
 * 他人の進捗を見る
 */
public class AdvancementViewer {
    /**
     * プレイヤーが他のプレイヤーの進捗を見る
     *
     * @param viewer 見るプレイヤー
     *               このプレイヤーに進捗を送信する
     * @param target 見られるプレイヤー
     */
    public void seePlayerAdvancements(Player viewer, Player target) {
        // 送信するデータ
        List<Object> toAdd = new ArrayList<>();
        Set<Object> toRemove = new HashSet<>();
        Map<Object, Object> toUpdate = new HashMap<>();

        // リフレクション経由で NMS オブジェクトを取得
        Object server = NMSUtils.getServer();
        Object advancementManager = NMSUtils.getAdvancements(server);
        Object targetHandle = NMSUtils.getHandle(target);
        Object playerAdvancements = NMSUtils.getPlayerAdvancements(targetHandle);

        // 全進捗を取得し、進捗状況を update に追加
        Collection<?> allAdvancements = NMSUtils.getAllAdvancements(advancementManager);
        for (Object advancementHolder : allAdvancements) {
            Object progress = NMSUtils.getOrStartProgress(playerAdvancements, advancementHolder);
            Object id = NMSUtils.getAdvancementId(advancementHolder);
            toUpdate.put(id, progress);
        }

        // 表示判定を行い、表示すべき進捗を toAdd に追加
        Object tree = NMSUtils.getAdvancementTree(advancementManager);
        Iterable<?> roots = NMSUtils.getRoots(tree);
        
        // 判定用のプロキシを作成
        Method getOrStartProgressMethod = NMSUtils.findMethodByReturnTypeName(playerAdvancements.getClass(), "AdvancementProgress", 1);
        Object predicate = NMSUtils.createPredicateProxy(playerAdvancements, getOrStartProgressMethod);
        Object consumer = NMSUtils.createConsumerProxy(toAdd);

        // 判定実行
        for (Object root : roots) {
            NMSUtils.evaluateVisibility(root, predicate, consumer);
        }

        // パケットを生成して送信
        Object viewerHandle = NMSUtils.getHandle(viewer);
        Object packet = NMSUtils.createUpdatePacket(true, toAdd, toRemove, toUpdate);
        NMSUtils.sendPacket(viewerHandle, packet);
    }

    /**
     * タブ閉じるパケットアダプターを登録する
     */
    public void register() {
        app.protocolManager.addPacketListener(new PacketAdapter(app, PacketType.Play.Client.ADVANCEMENTS) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                PacketContainer packetContainer = event.getPacket();
                Object packet = packetContainer.getHandle();
                String action = NMSUtils.getSeenAdvancementsAction(packet);

                // 進捗タブが開かれた
                if ("OPENED_TAB".equals(action)) {
                    onAdvancementTabOpen(event.getPlayer());
                }

                // 進捗タブが閉じられた
                if ("CLOSED_SCREEN".equals(action)) {
                    onAdvancementTabClose(event.getPlayer());
                }
            }
        });
    }

    /**
     * 進捗タブが開かれた
     *
     * @param viewer 見るプレイヤー
     */
    private void onAdvancementTabOpen(Player viewer) {
        // プレイヤーデータを取得
        PlayerData playerData = app.playerDataManager.getPlayerData(viewer);
        if (playerData.needUpdate) {
            // タイトルを消す
            viewer.sendTitle("", "", 0, 0, 0);

            // 進捗を更新する
            playerData.needUpdate = false;
        }
    }

    /**
     * 進捗タブが閉じられた
     *
     * @param viewer 見るプレイヤー
     */
    private void onAdvancementTabClose(Player viewer) {
        // プレイヤーデータを取得
        PlayerData playerData = app.playerDataManager.getPlayerData(viewer);
        // ターゲットをリセット
        playerData.targetQueue = null;
        // ID表示をリセット
        playerData.showId = false;
        // 元に戻す
        seePlayerAdvancements(viewer, viewer);
    }
}
