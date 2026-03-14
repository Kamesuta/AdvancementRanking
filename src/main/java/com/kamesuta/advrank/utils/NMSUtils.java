package com.kamesuta.advrank.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.*;
import java.util.Collection;

/**
 * NMSバージョンに依存しないリフレクションユーティリティ。
 */
public class NMSUtils {

    /** MinecraftServer を取得する */
    public static Object getServer() {
        try {
            Object craftServer = Bukkit.getServer();
            return craftServer.getClass().getMethod("getServer").invoke(craftServer);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** Bukkit Player から ServerPlayer を取得する */
    public static Object getHandle(Player player) {
        try {
            return player.getClass().getMethod("getHandle").invoke(player);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** ServerAdvancementManager を取得する */
    public static Object getAdvancements(Object server) {
        return invokeMethodByReturnType(server, "ServerAdvancementManager");
    }

    /** PlayerAdvancements を取得する */
    public static Object getPlayerAdvancements(Object player) {
        return invokeMethodByReturnType(player, "PlayerAdvancements");
    }

    /** AdvancementTree を取得する */
    public static Object getAdvancementTree(Object manager) {
        return invokeMethodByReturnType(manager, "AdvancementTree");
    }

    /** 全進捗のリストを取得する */
    public static Collection<?> getAllAdvancements(Object manager) {
        try {
            for (Method m : manager.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && Collection.class.isAssignableFrom(m.getReturnType())) {
                    return (Collection<?>) m.invoke(manager);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("getAllAdvancements メソッドが見つかりません");
    }

    /** 進捗IDを取得する */
    public static Object getAdvancementId(Object advancementHolder) {
        try {
            return advancementHolder.getClass().getMethod("id").invoke(advancementHolder);
        } catch (Exception e) {
            throw new RuntimeException("進捗IDの取得に失敗しました", e);
        }
    }

    /** 進捗の進捗状況を取得する */
    public static Object getOrStartProgress(Object playerAdvancements, Object advancementHolder) {
        try {
            Method m = findMethodByReturnTypeName(playerAdvancements.getClass(), "AdvancementProgress", 1);
            return m.invoke(playerAdvancements, advancementHolder);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 進捗タブのルート要素を取得する */
    public static Iterable<?> getRoots(Object tree) {
        try {
            for (Method m : tree.getClass().getMethods()) {
                if (m.getParameterCount() == 0 && Iterable.class.isAssignableFrom(m.getReturnType())) {
                    return (Iterable<?>) m.invoke(tree);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("roots メソッドが見つかりません");
    }

    /** 進捗の表示可否を判定する */
    public static void evaluateVisibility(Object root, Object predicateProxy, Object consumerProxy) {
        try {
            Class<?> evaluatorClass = Class.forName("net.minecraft.server.advancements.AdvancementVisibilityEvaluator");
            for (Method m : evaluatorClass.getMethods()) {
                if (Modifier.isStatic(m.getModifiers()) && m.getParameterCount() == 3) {
                    m.invoke(null, root, predicateProxy, consumerProxy);
                    return;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 進捗更新パケットを作成する */
    public static Object createUpdatePacket(boolean reset, Collection<?> toAdd, Collection<?> toRemove, Object toUpdate) {
        try {
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket");
            for (Constructor<?> ctor : packetClass.getConstructors()) {
                if (ctor.getParameterCount() == 5) {
                    return ctor.newInstance(reset, toAdd, toRemove, toUpdate, false);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        throw new RuntimeException("ClientboundUpdateAdvancementsPacket コンストラクタが見つかりません");
    }

    /** パケットを送信する */
    public static void sendPacket(Object player, Object packet) {
        try {
            Field connectionField = null;
            for (Field f : player.getClass().getFields()) {
                if (f.getName().equals("connection")) {
                    connectionField = f;
                    break;
                }
            }
            if (connectionField == null) {
                for (Field f : player.getClass().getDeclaredFields()) {
                    if (f.getType().getSimpleName().contains("PacketListener") || f.getType().getSimpleName().contains("GamePacket")) {
                        f.setAccessible(true);
                        connectionField = f;
                        break;
                    }
                }
            }
            Object connection = connectionField.get(player);
            for (Method m : connection.getClass().getMethods()) {
                if (m.getParameterCount() == 1 && m.getName().equals("send")) {
                    m.invoke(connection, packet);
                    return;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 進捗を完了しているか判定するプロキシ用述語を作成する */
    public static Object createPredicateProxy(Object playerAdvancements, final Method getOrStartProgress) {
        try {
            Class<?> evaluatorClass = Class.forName("net.minecraft.server.advancements.AdvancementVisibilityEvaluator");
            Class<?> predicateClass = evaluatorClass.getMethods()[0].getParameterTypes()[1];
            return Proxy.newProxyInstance(predicateClass.getClassLoader(), new Class<?>[]{predicateClass}, (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("toString")) return "NMSPredicateProxy";
                if (name.equals("hashCode")) return System.identityHashCode(proxy);
                if (name.equals("equals")) return proxy == args[0];
                try {
                    Object node = args[0];
                    Object holder = node.getClass().getMethod("holder").invoke(node);
                    Object progress = getOrStartProgress.invoke(playerAdvancements, holder);
                    return (Boolean) progress.getClass().getMethod("isDone").invoke(progress);
                } catch (Exception e) {
                    return false;
                }
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** 表示フラグに応じたプロキシ用コンシューマを作成する */
    public static Object createConsumerProxy(Collection<Object> toAdd) {
        try {
            Class<?> evaluatorClass = Class.forName("net.minecraft.server.advancements.AdvancementVisibilityEvaluator");
            Class<?> consumerClass = evaluatorClass.getMethods()[0].getParameterTypes()[2];
            return Proxy.newProxyInstance(consumerClass.getClassLoader(), new Class<?>[]{consumerClass}, (proxy, method, args) -> {
                String name = method.getName();
                if (name.equals("toString")) return "NMSConsumerProxy";
                if (name.equals("hashCode")) return System.identityHashCode(proxy);
                if (name.equals("equals")) return proxy == args[0];
                if (args.length >= 2 && args[1] instanceof Boolean flag && flag) {
                    try {
                        Object node = args[0];
                        Object holder = node.getClass().getMethod("holder").invoke(node);
                        toAdd.add(holder);
                    } catch (Exception ignored) {}
                }
                return null;
            });
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** ユーティリティ: 型名指定でメソッドを検索し実行 */
    private static Object invokeMethodByReturnType(Object obj, String typeName) {
        try {
            Method m = findMethodByReturnTypeName(obj.getClass(), typeName);
            return m.invoke(obj);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /** ユーティリティ: 型名指定でメソッドを検索 */
    public static Method findMethodByReturnTypeName(Class<?> clazz, String typeName) {
        return findMethodByReturnTypeName(clazz, typeName, 0);
    }

    /** ユーティリティ: 型名と引数個数指定でメソッドを検索 */
    public static Method findMethodByReturnTypeName(Class<?> clazz, String typeName, int paramCount) {
        for (Method m : clazz.getMethods()) {
            if (m.getParameterCount() == paramCount && m.getReturnType().getSimpleName().equals(typeName)) {
                return m;
            }
        }
        return null;
    }

    /** パケットのアクション名を取得する */
    public static String getSeenAdvancementsAction(Object packet) {
        try {
            // getAction() -> ServerboundSeenAdvancementsPacket.Action (enum)
            Method getAction = findMethodByReturnTypeName(packet.getClass(), "Action");
            if (getAction == null) {
                // 返り値型名で見つからない場合はメソッド名で探す
                for (Method m : packet.getClass().getMethods()) {
                    if (m.getParameterCount() == 0 && m.getReturnType().isEnum()) {
                        getAction = m;
                        break;
                    }
                }
            }
            if (getAction == null) throw new RuntimeException("getAction() が見つかりません");
            Object action = getAction.invoke(packet);
            // Enum の name() を取得
            return (String) action.getClass().getMethod("name").invoke(action);
        } catch (Exception e) {
            throw new RuntimeException("パケットアクションの取得に失敗しました", e);
        }
    }
}
