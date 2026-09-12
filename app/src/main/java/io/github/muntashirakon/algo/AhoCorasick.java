// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.muntashirakon.algo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AhoCorasick implements AutoCloseable {
    private static boolean nativeAvailable;

    static {
        try {
            System.loadLibrary("am");
            nativeAvailable = true;
        } catch (UnsatisfiedLinkError | SecurityException e) {
            // Tracker metadata must remain usable if the optional native accelerator is unavailable.
            nativeAvailable = false;
        }
    }

    private long nativeInstanceId;
    private final JavaMatcher fallback;
    private boolean closed;

    public AhoCorasick(String[] patterns) {
        if (nativeAvailable) {
            try {
                nativeInstanceId = createNative(patterns);
            } catch (UnsatisfiedLinkError e) {
                // The installed native library may not contain this optional entry point.
            }
        }
        fallback = nativeInstanceId == 0 ? new JavaMatcher(patterns) : null;
    }

    private native long createNative(String[] patterns);
    private native int[] searchNative(long instanceId, String text);
    private native void destroyNative(long instanceId);

    /** Search returns pattern indices, including overlapping and repeated matches. */
    public synchronized int[] search(String text) {
        if (closed) throw new IllegalStateException("Instance already closed");
        return fallback != null ? fallback.search(text) : searchNative(nativeInstanceId, text);
    }

    /** Synchronize with search so native memory cannot be freed while a search is using it. */
    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (nativeInstanceId != 0) {
            destroyNative(nativeInstanceId);
            nativeInstanceId = 0;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    /** The same trie/failure-link search as the native implementation, without JNI. */
    private static final class JavaMatcher {
        private static final class Node {
            final Map<Character, Node> children = new HashMap<>();
            final List<Integer> output = new ArrayList<>();
            Node fail;
        }

        private final Node root = new Node();

        JavaMatcher(String[] patterns) {
            for (int i = 0; i < patterns.length; ++i) {
                Node node = root;
                for (int j = 0; j < patterns[i].length(); ++j) {
                    char c = patterns[i].charAt(j);
                    Node next = node.children.get(c);
                    if (next == null) {
                        next = new Node();
                        node.children.put(c, next);
                    }
                    node = next;
                }
                node.output.add(i);
            }
            root.fail = root;
            ArrayDeque<Node> queue = new ArrayDeque<>();
            for (Node child : root.children.values()) {
                child.fail = root;
                queue.add(child);
            }
            while (!queue.isEmpty()) {
                Node node = queue.remove();
                for (Map.Entry<Character, Node> entry : node.children.entrySet()) {
                    char c = entry.getKey();
                    Node child = entry.getValue();
                    Node fail = node.fail;
                    while (fail != root && !fail.children.containsKey(c)) fail = fail.fail;
                    Node next = fail.children.get(c);
                    child.fail = next != null && next != child ? next : root;
                    child.output.addAll(child.fail.output);
                    queue.add(child);
                }
            }
        }

        int[] search(String text) {
            List<Integer> matches = new ArrayList<>();
            Node node = root;
            for (int i = 0; i < text.length(); ++i) {
                char c = text.charAt(i);
                while (node != root && !node.children.containsKey(c)) node = node.fail;
                Node next = node.children.get(c);
                if (next != null) node = next;
                matches.addAll(node.output);
            }
            int[] result = new int[matches.size()];
            for (int i = 0; i < result.length; ++i) result[i] = matches.get(i);
            return result;
        }
    }
}
