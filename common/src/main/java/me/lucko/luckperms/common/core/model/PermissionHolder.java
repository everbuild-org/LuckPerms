/*
 * Copyright (c) 2016 Lucko (Luck) <luck@lucko.me>
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in all
 *  copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 *  SOFTWARE.
 */

package me.lucko.luckperms.common.core.model;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;

import me.lucko.luckperms.api.Contexts;
import me.lucko.luckperms.api.LocalizedNode;
import me.lucko.luckperms.api.Node;
import me.lucko.luckperms.api.Tristate;
import me.lucko.luckperms.common.api.delegates.PermissionHolderDelegate;
import me.lucko.luckperms.common.caching.MetaHolder;
import me.lucko.luckperms.common.caching.handlers.CachedStateManager;
import me.lucko.luckperms.common.caching.handlers.GroupReference;
import me.lucko.luckperms.common.caching.handlers.HolderReference;
import me.lucko.luckperms.common.caching.holder.ExportNodesHolder;
import me.lucko.luckperms.common.caching.holder.GetAllNodesRequest;
import me.lucko.luckperms.common.commands.utils.Util;
import me.lucko.luckperms.common.config.ConfigKeys;
import me.lucko.luckperms.common.core.InheritanceInfo;
import me.lucko.luckperms.common.core.NodeFactory;
import me.lucko.luckperms.common.core.PriorityComparator;
import me.lucko.luckperms.common.core.TemporaryModifier;
import me.lucko.luckperms.common.plugin.LuckPermsPlugin;
import me.lucko.luckperms.common.utils.Cache;
import me.lucko.luckperms.common.utils.ExtractedContexts;
import me.lucko.luckperms.common.utils.ImmutableLocalizedNode;
import me.lucko.luckperms.common.utils.WeightComparator;
import me.lucko.luckperms.exceptions.ObjectAlreadyHasException;
import me.lucko.luckperms.exceptions.ObjectLacksException;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Represents an object that can hold permissions
 * For example a User or a Group
 */
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class PermissionHolder {

    /**
     * The UUID of the user / name of the group.
     * Used to prevent circular inheritance issues
     */
    @Getter
    private final String objectName;

    /**
     * Reference to the main plugin instance
     */
    @Getter(AccessLevel.PROTECTED)
    private final LuckPermsPlugin plugin;

    /**
     * The holders persistent nodes
     */
    private final Set<Node> nodes = new HashSet<>();

    /**
     * The holders transient nodes
     */
    private final Set<Node> transientNodes = new HashSet<>();

    /**
     * Lock used by Storage implementations to prevent concurrent read/writes
     */
    @Getter
    private final Lock ioLock = new ReentrantLock();

    @Getter
    private final Set<Runnable> stateListeners = ConcurrentHashMap.newKeySet();


    /*
     * CACHES - cache the result of a number of methods in this class, until they are invalidated.
     */

    /* Internal Caches - only depend on the state of this instance. */

    // Just holds immutable copies of the node sets.
    private Cache<ImmutableSet<Node>> enduringCache = new Cache<>(() -> {
        synchronized (nodes) {
            return ImmutableSet.copyOf(nodes);
        }
    });
    private Cache<ImmutableSet<Node>> transientCache = new Cache<>(() -> {
        synchronized (transientNodes) {
            return ImmutableSet.copyOf(transientNodes);
        }
    });

    // Merges transient and persistent nodes together, and converts Node instances to a localized form.
    private Cache<ImmutableSortedSet<LocalizedNode>> cache = new Cache<>(() -> cacheApply(false));
    // Same as the cache above, except this merges temporary values with any permanent values if any are matching.
    private Cache<ImmutableSortedSet<LocalizedNode>> mergedCache = new Cache<>(() -> cacheApply(true));

    /* External Caches - may depend on the state of other instances. */

    private LoadingCache<GetAllNodesRequest, SortedSet<LocalizedNode>> getAllNodesCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(new CacheLoader<GetAllNodesRequest, SortedSet<LocalizedNode>>() {
                @Override
                public SortedSet<LocalizedNode> load(GetAllNodesRequest getAllNodesHolder) {
                    return getAllNodesCacheApply(getAllNodesHolder);
                }
            });
    private LoadingCache<ExtractedContexts, Set<LocalizedNode>> getAllNodesFilteredCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(new CacheLoader<ExtractedContexts, Set<LocalizedNode>>() {
                @Override
                public Set<LocalizedNode> load(ExtractedContexts extractedContexts) throws Exception {
                    return getAllNodesFilteredApply(extractedContexts);
                }
            });
    private LoadingCache<ExportNodesHolder, Map<String, Boolean>> exportNodesCache = CacheBuilder.newBuilder()
            .expireAfterAccess(10, TimeUnit.MINUTES)
            .build(new CacheLoader<ExportNodesHolder, Map<String, Boolean>>() {
                @Override
                public Map<String, Boolean> load(ExportNodesHolder exportNodesHolder) throws Exception {
                    return exportNodesApply(exportNodesHolder);
                }
            });



    /* Caching apply methods. Are just called by the caching instances to gather data about the instance. */

    protected void forceCleanup() {
        getAllNodesCache.cleanUp();
        getAllNodesFilteredCache.cleanUp();
        exportNodesCache.cleanUp();
    }

    private void invalidateCache(boolean enduring) {
        if (enduring) {
            enduringCache.invalidate();
        } else {
            transientCache.invalidate();
        }
        cache.invalidate();
        mergedCache.invalidate();

        // Invalidate inheritance caches
        getAllNodesCache.invalidateAll();
        getAllNodesFilteredCache.invalidateAll();
        exportNodesCache.invalidateAll();

        // Invalidate listeners
        for (Runnable r : stateListeners) {
            try {
                r.run();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // Get previous references
        Set<HolderReference> refs = plugin.getCachedStateManager().getInheritances(toReference());

        // Declare new state to the state manager
        declareState();

        // Add all new references affected by the state change.
        refs.addAll(plugin.getCachedStateManager().getInheritances(toReference()));

        // Invalidate all affected children.
        CachedStateManager.invalidateInheritances(plugin, refs);
    }

    public void invalidateInheritanceCaches() {
        getAllNodesCache.invalidateAll();
        getAllNodesFilteredCache.invalidateAll();
        exportNodesCache.invalidateAll();
        declareState();
    }

    private ImmutableSortedSet<LocalizedNode> cacheApply(boolean mergeTemp) {
        TreeSet<LocalizedNode> combined = new TreeSet<>(PriorityComparator.reverse());
        Set<Node> enduring = getNodes();
        if (!enduring.isEmpty()) {
            combined.addAll(enduring.stream()
                    .map(n -> ImmutableLocalizedNode.of(n, getObjectName()))
                    .collect(Collectors.toList())
            );
        }
        Set<Node> tran = getTransientNodes();
        if (!tran.isEmpty()) {
            combined.addAll(tran.stream()
                    .map(n -> ImmutableLocalizedNode.of(n, getObjectName()))
                    .collect(Collectors.toList())
            );
        }

        Iterator<LocalizedNode> it = combined.iterator();
        Set<LocalizedNode> higherPriority = new HashSet<>();

        iterate:
        while (it.hasNext()) {
            LocalizedNode entry = it.next();
            for (LocalizedNode h : higherPriority) {
                if (mergeTemp ? entry.getNode().equalsIgnoringValueOrTemp(h.getNode()) : entry.getNode().almostEquals(h.getNode())) {
                    it.remove();
                    continue iterate;
                }
            }
            higherPriority.add(entry);
        }
        return ImmutableSortedSet.copyOfSorted(combined);
    }

    private SortedSet<LocalizedNode> getAllNodesCacheApply(GetAllNodesRequest getAllNodesHolder) {
        // Expand the holder.
        Set<String> excludedGroups = new HashSet<>(getAllNodesHolder.getExcludedGroups());
        ExtractedContexts contexts = getAllNodesHolder.getContexts();

        // Don't register users, as they cannot be inherited.
        if (!(this instanceof User)) {
            excludedGroups.add(getObjectName().toLowerCase());
        }

        // Get the objects base permissions.
        SortedSet<LocalizedNode> all = new TreeSet<>((SortedSet<LocalizedNode>) getPermissions(true));

        Contexts context = contexts.getContexts();
        String server = contexts.getServer();
        String world = contexts.getWorld();

        Set<Node> parents = all.stream()
                .map(LocalizedNode::getNode)
                .filter(Node::getValue)
                .filter(Node::isGroupNode)
                .filter(n -> n.shouldApplyOnServer(server, context.isApplyGlobalGroups(), plugin.getConfiguration().get(ConfigKeys.APPLYING_REGEX)))
                .filter(n -> n.shouldApplyOnWorld(world, context.isApplyGlobalWorldGroups(), plugin.getConfiguration().get(ConfigKeys.APPLYING_REGEX)))
                .filter(n -> n.shouldApplyWithContext(contexts.getContextSet(), false))
                .collect(Collectors.toSet());

        // Resolve & sort parents into order before we apply.
        TreeSet<Map.Entry<Integer, Group>> sortedParents = new TreeSet<>(WeightComparator.INSTANCE.reversed());
        for (Node node : parents) {
            Group group = plugin.getGroupManager().getIfLoaded(node.getGroupName());
            if (group != null) {
                sortedParents.add(Maps.immutableEntry(group.getWeight().orElse(0), group));
            }
        }

        for (Map.Entry<Integer, Group> e : sortedParents) {
            if (excludedGroups.contains(e.getValue().getObjectName().toLowerCase())) {
                continue;
            }

            inherited:
            for (LocalizedNode inherited : e.getValue().getAllNodes(excludedGroups, contexts)) {
                for (LocalizedNode existing : all) {
                    if (existing.getNode().almostEquals(inherited.getNode())) {
                        continue inherited;
                    }
                }

                all.add(inherited);
            }
        }

        return all;
    }

    private Set<LocalizedNode> getAllNodesFilteredApply(ExtractedContexts contexts) {
        Contexts context = contexts.getContexts();
        String server = contexts.getServer();
        String world = contexts.getWorld();

        SortedSet<LocalizedNode> allNodes;
        if (context.isApplyGroups()) {
            allNodes = getAllNodes(null, contexts);
        } else {
            allNodes = new TreeSet<>((SortedSet<LocalizedNode>) getPermissions(true));
        }

        allNodes.removeIf(node ->
                !node.isGroupNode() && (
                        !node.shouldApplyOnServer(server, context.isIncludeGlobal(), plugin.getConfiguration().get(ConfigKeys.APPLYING_REGEX)) ||
                        !node.shouldApplyOnWorld(world, context.isIncludeGlobalWorld(), plugin.getConfiguration().get(ConfigKeys.APPLYING_REGEX)) ||
                        !node.shouldApplyWithContext(contexts.getContextSet(), false)
                )
        );

        Set<LocalizedNode> perms = ConcurrentHashMap.newKeySet();

        all:
        for (LocalizedNode ln : allNodes) {
            // Force higher priority nodes to override
            for (LocalizedNode alreadyIn : perms) {
                if (ln.getNode().getPermission().equals(alreadyIn.getNode().getPermission())) {
                    continue all;
                }
            }

            perms.add(ln);
        }

        return perms;
    }

    private Map<String, Boolean> exportNodesApply(ExportNodesHolder exportNodesHolder) {
        Contexts context = exportNodesHolder.getContexts();
        Boolean lowerCase = exportNodesHolder.getLowerCase();

        Map<String, Boolean> perms = new HashMap<>();

        for (LocalizedNode ln : getAllNodesFiltered(ExtractedContexts.generate(context))) {
            Node node = ln.getNode();

            perms.put(lowerCase ? node.getPermission().toLowerCase() : node.getPermission(), node.getValue());

            if (plugin.getConfiguration().get(ConfigKeys.APPLYING_SHORTHAND)) {
                List<String> sh = node.resolveShorthand();
                if (!sh.isEmpty()) {
                    sh.stream().map(s -> lowerCase ? s.toLowerCase() : s)
                            .filter(s -> !perms.containsKey(s))
                            .forEach(s -> perms.put(s, node.getValue()));
                }
            }
        }

        return ImmutableMap.copyOf(perms);
    }

    protected void declareState() {
        plugin.getCachedStateManager().putAll(toReference(), getGroupReferences());
    }

    public abstract String getFriendlyName();
    public abstract HolderReference<?> toReference();
    public abstract PermissionHolderDelegate getDelegate();

    public Set<Node> getNodes() {
        return enduringCache.get();
    }

    public void setNodes(Map<String, Boolean> nodes) {
        Set<Node> set = nodes.entrySet().stream()
                .map(e -> NodeFactory.fromSerialisedNode(e.getKey(), e.getValue()))
                .collect(Collectors.toSet());

        setNodes(set);
    }

    public void setNodes(Set<Node> set) {
        synchronized (nodes) {
            if (nodes.equals(set)) {
                return;
            }

            nodes.clear();
            nodes.addAll(set);
        }
        invalidateCache(true);
    }

    public boolean removeIf(Predicate<Node> predicate) {
        boolean result;
        ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());

        synchronized (nodes) {
            result = nodes.removeIf(predicate);
        }

        if (!result) {
            return false;
        }

        invalidateCache(true);
        ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());
        plugin.getApiProvider().getEventFactory().handleNodeClear(this, before, after);
        return true;
    }

    public Set<Node> getTransientNodes() {
        return transientCache.get();
    }

    public void setTransientNodes(Set<Node> set) {
        synchronized (transientNodes) {
            if (transientNodes.equals(set)) {
                return;
            }

            transientNodes.clear();
            transientNodes.addAll(set);
        }
        invalidateCache(false);
    }

    public boolean removeIfTransient(Predicate<Node> predicate) {
        boolean result;

        synchronized (nodes) {
            result = transientNodes.removeIf(predicate);
        }

        if (result) {
            invalidateCache(false);
        }

        return result;
    }

    /**
     * Combines and returns this holders nodes in a priority order.
     *
     * @param mergeTemp if the temporary nodes should be merged together with permanent nodes
     * @return the holders transient and permanent nodes
     */
    public SortedSet<LocalizedNode> getPermissions(boolean mergeTemp) {
        return mergeTemp ? mergedCache.get() : cache.get();
    }

    /**
     * Resolves inherited nodes and returns them
     *
     * @param excludedGroups a list of groups to exclude
     * @param contexts       context to decide if groups should be applied
     * @return a set of nodes
     */
    public SortedSet<LocalizedNode> getAllNodes(Collection<String> excludedGroups, ExtractedContexts contexts) {
        return getAllNodesCache.getUnchecked(GetAllNodesRequest.of(excludedGroups == null ? ImmutableSet.of() : ImmutableSet.copyOf(excludedGroups), contexts));
    }

    /**
     * Gets all of the nodes that this holder has (and inherits), given the context
     *
     * @param contexts the context for this request
     * @return a map of permissions
     */
    public Set<LocalizedNode> getAllNodesFiltered(ExtractedContexts contexts) {
        return getAllNodesFilteredCache.getUnchecked(contexts);
    }

    /**
     * Converts the output of {@link #getAllNodesFiltered(ExtractedContexts)}, and expands shorthand perms
     *
     * @param context the context for this request
     * @return a map of permissions
     */
    public Map<String, Boolean> exportNodes(Contexts context, boolean lowerCase) {
        return exportNodesCache.getUnchecked(ExportNodesHolder.of(context, lowerCase));
    }

    public MetaHolder accumulateMeta(MetaHolder holder, Set<String> excludedGroups, ExtractedContexts contexts) {
        if (holder == null) {
            holder = new MetaHolder(
                    plugin.getConfiguration().get(ConfigKeys.PREFIX_FORMATTING_OPTIONS).copy(),
                    plugin.getConfiguration().get(ConfigKeys.SUFFIX_FORMATTING_OPTIONS).copy()
            );
        }

        if (excludedGroups == null) {
            excludedGroups = new HashSet<>();
        }

        excludedGroups.add(getObjectName().toLowerCase());

        Contexts context = contexts.getContexts();
        String server = contexts.getServer();
        String world = contexts.getWorld();

        SortedSet<LocalizedNode> all = new TreeSet<>((SortedSet<LocalizedNode>) getPermissions(true));
        for (LocalizedNode ln : all) {
            Node n = ln.getNode();

            if (!n.getValue()) continue;
            if (!n.isMeta() && !n.isPrefix() && !n.isSuffix()) continue;
            if (!n.shouldApplyOnServer(server, context.isIncludeGlobal(), false)) continue;
            if (!n.shouldApplyOnWorld(world, context.isIncludeGlobalWorld(), false)) continue;
            if (!n.shouldApplyWithContext(contexts.getContextSet(), false)) continue;

            holder.accumulateNode(ln);
        }

        OptionalInt w = getWeight();
        if (w.isPresent()) {
            holder.accumulateWeight(w.getAsInt());
        }

        Set<Node> parents = all.stream()
                .map(LocalizedNode::getNode)
                .filter(Node::getValue)
                .filter(Node::isGroupNode)
                .collect(Collectors.toSet());

        parents.removeIf(node ->
                !node.shouldApplyOnServer(server, context.isApplyGlobalGroups(), plugin.getConfiguration().get(ConfigKeys.APPLYING_REGEX)) ||
                !node.shouldApplyOnWorld(world, context.isApplyGlobalWorldGroups(), plugin.getConfiguration().get(ConfigKeys.APPLYING_REGEX)) ||
                !node.shouldApplyWithContext(contexts.getContextSet(), false)
        );

        TreeSet<Map.Entry<Integer, Group>> sortedParents = new TreeSet<>(Util.META_COMPARATOR.reversed());
        for (Node node : parents) {
            Group group = plugin.getGroupManager().getIfLoaded(node.getGroupName());

            if (group == null) {
                continue;
            }

            if (excludedGroups.contains(group.getObjectName().toLowerCase())) {
                continue;
            }

            sortedParents.add(Maps.immutableEntry(group.getWeight().orElse(0), group));
        }

        for (Map.Entry<Integer, Group> e : sortedParents) {
            e.getValue().accumulateMeta(holder, excludedGroups, contexts);
        }

        return holder;
    }

    /**
     * Removes temporary permissions that have expired
     *
     * @return true if permissions had expired and were removed
     */
    public boolean auditTemporaryPermissions() {
        boolean work = false;
        Set<Node> removed = new HashSet<>();

        ImmutableSet<Node> before = ImmutableSet.copyOf(getPermissions(true));

        synchronized (nodes) {
            Iterator<Node> it = nodes.iterator();
            while (it.hasNext()) {
                Node entry = it.next();
                if (entry.hasExpired()) {
                    removed.add(entry);
                    work = true;
                    it.remove();
                }
            }
        }

        if (work) {
            invalidateCache(true);
            work = false;
        }

        synchronized (transientNodes) {
            Iterator<Node> it = transientNodes.iterator();
            while (it.hasNext()) {
                Node entry = it.next();
                if (entry.hasExpired()) {
                    removed.add(entry);
                    work = true;
                    it.remove();
                }
            }
        }

        if (work) {
            invalidateCache(false);
        }

        if (removed.isEmpty()) {
            return false;
        }

        ImmutableSet<Node> after = ImmutableSet.copyOf(getPermissions(true));

        for (Node r : removed) {
            plugin.getApiProvider().getEventFactory().handleNodeRemove(r, this, before, after);
        }

        return true;
    }

    public Optional<Node> getAlmostEquals(Node node, boolean t) {
        for (Node n : t ? getTransientNodes() : getNodes()) {
            if (n.almostEquals(node)) {
                return Optional.of(n);
            }
        }

        return Optional.empty();
    }

    /**
     * Check if the holder has a permission node
     *
     * @param node the node to check
     * @param checkTransient    whether to check transient nodes
     * @return a tristate
     */
    public Tristate hasPermission(Node node, boolean checkTransient) {
        return getAlmostEquals(node, checkTransient).map(Node::getTristate).orElse(Tristate.UNDEFINED);
    }

    public Tristate hasPermission(Node node) {
        return hasPermission(node, false);
    }

    public boolean hasPermission(String node, boolean value) {
        return hasPermission(NodeFactory.make(node, value)).asBoolean() == value;
    }

    public boolean hasPermission(String node, boolean value, String server) {
        return hasPermission(NodeFactory.make(node, value, server)).asBoolean() == value;
    }

    public boolean hasPermission(String node, boolean value, String server, String world) {
        return hasPermission(NodeFactory.make(node, value, server, world)).asBoolean() == value;
    }

    public boolean hasPermission(String node, boolean value, boolean temporary) {
        return hasPermission(NodeFactory.make(node, value, temporary)).asBoolean() == value;
    }

    public boolean hasPermission(String node, boolean value, String server, boolean temporary) {
        return hasPermission(NodeFactory.make(node, value, server, temporary)).asBoolean() == value;
    }

    public boolean hasPermission(String node, boolean value, String server, String world, boolean temporary) {
        return hasPermission(NodeFactory.make(node, value, server, world, temporary)).asBoolean() == value;
    }

    /**
     * Check if the holder inherits a node
     *
     * @param node the node to check
     * @return the result of the lookup
     */
    public InheritanceInfo inheritsPermissionInfo(Node node) {
        for (LocalizedNode n : getAllNodes(null, ExtractedContexts.generate(Contexts.allowAll()))) {
            if (n.getNode().almostEquals(node)) {
                return InheritanceInfo.of(n);
            }
        }

        return InheritanceInfo.empty();
    }

    /**
     * Check if the holder inherits a node
     *
     * @param node the node to check
     * @return the Tristate result
     */
    public Tristate inheritsPermission(Node node) {
        return inheritsPermissionInfo(node).getResult();
    }

    public boolean inheritsPermission(String node, boolean value) {
        return inheritsPermission(NodeFactory.make(node, value)).asBoolean() == value;
    }

    public boolean inheritsPermission(String node, boolean value, String server) {
        return inheritsPermission(NodeFactory.make(node, value, server)).asBoolean() == value;
    }

    public boolean inheritsPermission(String node, boolean value, String server, String world) {
        return inheritsPermission(NodeFactory.make(node, value, server, world)).asBoolean() == value;
    }

    public boolean inheritsPermission(String node, boolean value, boolean temporary) {
        return inheritsPermission(NodeFactory.make(node, value, temporary)).asBoolean() == value;
    }

    public boolean inheritsPermission(String node, boolean value, String server, boolean temporary) {
        return inheritsPermission(NodeFactory.make(node, value, server, temporary)).asBoolean() == value;
    }

    public boolean inheritsPermission(String node, boolean value, String server, String world, boolean temporary) {
        return inheritsPermission(NodeFactory.make(node, value, server, world, temporary)).asBoolean() == value;
    }

    /**
     * Sets a permission node
     *
     * @param node the node to set
     * @throws ObjectAlreadyHasException if the holder has this permission already
     */
    public void setPermission(Node node) throws ObjectAlreadyHasException {
        if (hasPermission(node, false) != Tristate.UNDEFINED) {
            throw new ObjectAlreadyHasException();
        }

        ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());

        synchronized (nodes) {
            nodes.add(node);
        }
        invalidateCache(true);

        ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());

        plugin.getApiProvider().getEventFactory().handleNodeAdd(node, this, before, after);
    }

    public void setPermissionUnchecked(Node node) {
        try {
            setPermission(node);
        } catch (ObjectAlreadyHasException ignored) {}
    }

    /**
     * Sets a permission node, applying a temporary modifier if the node is temporary.
     * @param node the node to set
     * @param modifier the modifier to use for the operation
     * @return the node that was actually set, respective of the modifier
     * @throws ObjectAlreadyHasException if the holder has this permission set already, respective of the modifier
     */
    public Node setPermission(Node node, TemporaryModifier modifier) throws ObjectAlreadyHasException {
        // If the node is temporary, we should take note of the modifier
        if (node.isTemporary()) {
            if (modifier == TemporaryModifier.ACCUMULATE) {
                // Try to accumulate with an existing node
                Optional<Node> existing = getAlmostEquals(node, false);

                // An existing node was found
                if (existing.isPresent()) {
                    Node previous = existing.get();

                    // Create a new node with the same properties, but add the expiry dates together
                    Node newNode = NodeFactory.builderFromExisting(node).setExpiry(previous.getExpiryUnixTime() + node.getSecondsTilExpiry()).build();

                    ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());

                    // Remove the old node & add the new one.
                    synchronized (nodes) {
                        nodes.remove(previous);
                        nodes.add(newNode);
                    }

                    invalidateCache(true);
                    ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());
                    plugin.getApiProvider().getEventFactory().handleNodeAdd(newNode, this, before, after);
                    return newNode;
                }

            } else if (modifier == TemporaryModifier.REPLACE) {
                // Try to replace an existing node
                Optional<Node> existing = getAlmostEquals(node, false);

                // An existing node was found
                if (existing.isPresent()) {
                    Node previous = existing.get();

                    // Only replace if the new expiry time is greater than the old one.
                    if (node.getExpiryUnixTime() > previous.getExpiryUnixTime()) {

                        ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());

                        synchronized (nodes) {
                            nodes.remove(previous);
                            nodes.add(node);
                        }

                        invalidateCache(true);
                        ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());
                        plugin.getApiProvider().getEventFactory().handleNodeAdd(node, this, before, after);
                        return node;
                    }
                }
            }

            // DENY behaviour is the default anyways.
        }

        // Fallback to the normal handling.
        setPermission(node);
        return node;
    }

    public void setPermissionUnchecked(Node node, TemporaryModifier modifier) {
        try {
            setPermission(node, modifier);
        } catch (ObjectAlreadyHasException ignored) {}
    }

    /**
     * Sets a transient permission node
     *
     * @param node the node to set
     * @throws ObjectAlreadyHasException if the holder has this permission already
     */
    public void setTransientPermission(Node node) throws ObjectAlreadyHasException {
        if (hasPermission(node, true) != Tristate.UNDEFINED) {
            throw new ObjectAlreadyHasException();
        }

        ImmutableSet<Node> before = ImmutableSet.copyOf(getTransientNodes());

        synchronized (transientNodes) {
            transientNodes.add(node);
        }
        invalidateCache(false);

        ImmutableSet<Node> after = ImmutableSet.copyOf(getTransientNodes());

        plugin.getApiProvider().getEventFactory().handleNodeAdd(node, this, before, after);
    }

    public void setTransientPermissionUnchecked(Node node) {
        try {
            setTransientPermission(node);
        } catch (ObjectAlreadyHasException ignored) {}
    }

    public void setPermission(String node, boolean value) throws ObjectAlreadyHasException {
        setPermission(NodeFactory.make(node, value));
    }

    public void setPermission(String node, boolean value, String server) throws ObjectAlreadyHasException {
        setPermission(NodeFactory.make(node, value, server));
    }

    public void setPermission(String node, boolean value, String server, String world) throws ObjectAlreadyHasException {
        setPermission(NodeFactory.make(node, value, server, world));
    }

    public void setPermission(String node, boolean value, long expireAt) throws ObjectAlreadyHasException {
        setPermission(NodeFactory.make(node, value, expireAt));
    }

    public void setPermission(String node, boolean value, String server, long expireAt) throws ObjectAlreadyHasException {
        setPermission(NodeFactory.make(node, value, server, expireAt));
    }

    public void setPermission(String node, boolean value, String server, String world, long expireAt) throws ObjectAlreadyHasException {
        setPermission(NodeFactory.make(node, value, server, world, expireAt));
    }

    /**
     * Unsets a permission node
     *
     * @param node the node to unset
     * @throws ObjectLacksException if the holder doesn't have this node already
     */
    public void unsetPermission(Node node) throws ObjectLacksException {
        if (hasPermission(node, false) == Tristate.UNDEFINED) {
            throw new ObjectLacksException();
        }

        ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());

        synchronized (nodes) {
            nodes.removeIf(e -> e.almostEquals(node));
        }
        invalidateCache(true);

        ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());
        plugin.getApiProvider().getEventFactory().handleNodeRemove(node, this, before, after);
    }

    public void unsetPermissionUnchecked(Node node) {
        try {
            unsetPermission(node);
        } catch (ObjectLacksException ignored) {}
    }

    /**
     * Unsets a permission node
     *
     * @param node the node to unset
     * @throws ObjectLacksException if the holder doesn't have this node already
     */
    public void unsetPermissionExact(Node node) throws ObjectLacksException {
        ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());

        synchronized (nodes) {
            nodes.removeIf(e -> e.equals(node));
        }
        invalidateCache(true);

        ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());

        if (before.size() == after.size()) {
            throw new ObjectLacksException();
        }

        plugin.getApiProvider().getEventFactory().handleNodeRemove(node, this, before, after);
    }

    /**
     * Unsets a transient permission node
     *
     * @param node the node to unset
     * @throws ObjectLacksException if the holder doesn't have this node already
     */
    public void unsetTransientPermission(Node node) throws ObjectLacksException {
        if (hasPermission(node, true) == Tristate.UNDEFINED) {
            throw new ObjectLacksException();
        }

        ImmutableSet<Node> before = ImmutableSet.copyOf(getTransientNodes());

        synchronized (transientNodes) {
            transientNodes.removeIf(e -> e.almostEquals(node));
        }
        invalidateCache(false);

        ImmutableSet<Node> after = ImmutableSet.copyOf(getTransientNodes());
        plugin.getApiProvider().getEventFactory().handleNodeRemove(node, this, before, after);
    }

    public void unsetTransientPermissionUnchecked(Node node) {
        try {
            unsetTransientPermission(node);
        } catch (ObjectLacksException ignored) {}
    }

    public void unsetPermission(String node, boolean temporary) throws ObjectLacksException {
        unsetPermission(NodeFactory.make(node, temporary));
    }

    public void unsetPermission(String node) throws ObjectLacksException {
        unsetPermission(NodeFactory.make(node));
    }

    public void unsetPermission(String node, String server) throws ObjectLacksException {
        unsetPermission(NodeFactory.make(node, server));
    }

    public void unsetPermission(String node, String server, String world) throws ObjectLacksException {
        unsetPermission(NodeFactory.make(node, server, world));
    }

    public void unsetPermission(String node, String server, boolean temporary) throws ObjectLacksException {
        unsetPermission(NodeFactory.make(node, server, temporary));
    }

    public void unsetPermission(String node, String server, String world, boolean temporary) throws ObjectLacksException {
        unsetPermission(NodeFactory.make(node, server, world, temporary));
    }

    public boolean inheritsGroup(Group group) {
        return group.getName().equalsIgnoreCase(this.getObjectName()) || hasPermission("group." + group.getName(), true);
    }

    public boolean inheritsGroup(Group group, String server) {
        return group.getName().equalsIgnoreCase(this.getObjectName()) || hasPermission("group." + group.getName(), true, server);
    }

    public boolean inheritsGroup(Group group, String server, String world) {
        return group.getName().equalsIgnoreCase(this.getObjectName()) || hasPermission("group." + group.getName(), true, server, world);
    }

    public void setInheritGroup(Group group) throws ObjectAlreadyHasException {
        if (group.getName().equalsIgnoreCase(this.getObjectName())) {
            throw new ObjectAlreadyHasException();
        }

        setPermission("group." + group.getName(), true);
    }

    public void setInheritGroup(Group group, String server) throws ObjectAlreadyHasException {
        if (group.getName().equalsIgnoreCase(this.getObjectName())) {
            throw new ObjectAlreadyHasException();
        }

        setPermission("group." + group.getName(), true, server);
    }

    public void setInheritGroup(Group group, String server, String world) throws ObjectAlreadyHasException {
        if (group.getName().equalsIgnoreCase(this.getObjectName())) {
            throw new ObjectAlreadyHasException();
        }

        setPermission("group." + group.getName(), true, server, world);
    }

    public void setInheritGroup(Group group, long expireAt) throws ObjectAlreadyHasException {
        if (group.getName().equalsIgnoreCase(this.getObjectName())) {
            throw new ObjectAlreadyHasException();
        }

        setPermission("group." + group.getName(), true, expireAt);
    }

    public void setInheritGroup(Group group, String server, long expireAt) throws ObjectAlreadyHasException {
        if (group.getName().equalsIgnoreCase(this.getObjectName())) {
            throw new ObjectAlreadyHasException();
        }

        setPermission("group." + group.getName(), true, server, expireAt);
    }

    public void setInheritGroup(Group group, String server, String world, long expireAt) throws ObjectAlreadyHasException {
        if (group.getName().equalsIgnoreCase(this.getObjectName())) {
            throw new ObjectAlreadyHasException();
        }

        setPermission("group." + group.getName(), true, server, world, expireAt);
    }

    public void unsetInheritGroup(Group group) throws ObjectLacksException {
        unsetPermission("group." + group.getName());
    }

    public void unsetInheritGroup(Group group, boolean temporary) throws ObjectLacksException {
        unsetPermission("group." + group.getName(), temporary);
    }

    public void unsetInheritGroup(Group group, String server) throws ObjectLacksException {
        unsetPermission("group." + group.getName(), server);
    }

    public void unsetInheritGroup(Group group, String server, String world) throws ObjectLacksException {
        unsetPermission("group." + group.getName(), server, world);
    }

    public void unsetInheritGroup(Group group, String server, boolean temporary) throws ObjectLacksException {
        unsetPermission("group." + group.getName(), server, temporary);
    }

    public void unsetInheritGroup(Group group, String server, String world, boolean temporary) throws ObjectLacksException {
        unsetPermission("group." + group.getName(), server, world, temporary);
    }

    /**
     * Clear all of the holders permission nodes
     */
    public void clearNodes() {
        ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());
        synchronized (nodes) {
            nodes.clear();
        }
        invalidateCache(true);
        ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());
        plugin.getApiProvider().getEventFactory().handleNodeClear(this, before, after);
    }

    public void clearNodes(String server) {
        String finalServer = Optional.ofNullable(server).orElse("global");

        ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());
        synchronized (nodes) {
            if (!nodes.removeIf(n -> n.getServer().orElse("global").equalsIgnoreCase(finalServer))) {
                return;
            }
        }
        invalidateCache(true);
        ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());
        plugin.getApiProvider().getEventFactory().handleNodeClear(this, before, after);
    }

    public void clearNodes(String server, String world) {
        String finalServer = Optional.ofNullable(server).orElse("global");
        String finalWorld = Optional.ofNullable(world).orElse("null");

        ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());
        synchronized (nodes) {
            boolean b = nodes.removeIf(n ->
                    n.getServer().orElse("global").equalsIgnoreCase(finalServer) &&
                            n.getWorld().orElse("null").equalsIgnoreCase(finalWorld));
            if (!b) {
                return;
            }
        }
        invalidateCache(true);
        ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());
        plugin.getApiProvider().getEventFactory().handleNodeClear(this, before, after);
    }

    public void clearParents() {
        ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());

        synchronized (nodes) {
            boolean b = nodes.removeIf(Node::isGroupNode);
            if (!b) {
                return;
            }
        }
        if (this instanceof User) {
            plugin.getUserManager().giveDefaultIfNeeded((User) this, false);
        }
        invalidateCache(true);
        ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());
        plugin.getApiProvider().getEventFactory().handleNodeClear(this, before, after);
    }

    public void clearParents(String server) {
        String finalServer = Optional.ofNullable(server).orElse("global");

        ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());
        synchronized (nodes) {
            boolean b = nodes.removeIf(n ->
                    n.isGroupNode() && n.getServer().orElse("global").equalsIgnoreCase(finalServer)
            );
            if (!b) {
                return;
            }
        }
        if (this instanceof User) {
            plugin.getUserManager().giveDefaultIfNeeded((User) this, false);
        }
        invalidateCache(true);
        ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());
        plugin.getApiProvider().getEventFactory().handleNodeClear(this, before, after);
    }

    public void clearParents(String server, String world) {
        String finalServer = Optional.ofNullable(server).orElse("global");
        String finalWorld = Optional.ofNullable(world).orElse("null");

        ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());
        synchronized (nodes) {
            boolean b = nodes.removeIf(n ->
                    n.isGroupNode() &&
                            n.getServer().orElse("global").equalsIgnoreCase(finalServer) &&
                            n.getWorld().orElse("null").equalsIgnoreCase(finalWorld)
            );
            if (!b) {
                return;
            }
        }
        if (this instanceof User) {
            plugin.getUserManager().giveDefaultIfNeeded((User) this, false);
        }
        invalidateCache(true);
        ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());
        plugin.getApiProvider().getEventFactory().handleNodeClear(this, before, after);
    }

    public void clearMeta() {
        ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());

        synchronized (nodes) {
            if (!nodes.removeIf(n -> n.isMeta() || n.isPrefix() || n.isSuffix())) {
                return;
            }
        }
        invalidateCache(true);
        ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());
        plugin.getApiProvider().getEventFactory().handleNodeClear(this, before, after);
    }

    public void clearMeta(String server) {
        String finalServer = Optional.ofNullable(server).orElse("global");

        ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());
        synchronized (nodes) {
            boolean b = nodes.removeIf(n ->
                    (n.isMeta() || n.isPrefix() || n.isSuffix()) &&
                            n.getServer().orElse("global").equalsIgnoreCase(finalServer)
            );
            if (!b) {
                return;
            }
        }
        invalidateCache(true);
        ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());
        plugin.getApiProvider().getEventFactory().handleNodeClear(this, before, after);
    }

    public void clearMeta(String server, String world) {
        String finalServer = Optional.ofNullable(server).orElse("global");
        String finalWorld = Optional.ofNullable(world).orElse("null");

        ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());
        synchronized (nodes) {
            boolean b = nodes.removeIf(n ->
                    (n.isMeta() || n.isPrefix() || n.isSuffix()) && (
                            n.getServer().orElse("global").equalsIgnoreCase(finalServer) &&
                                    n.getWorld().orElse("null").equalsIgnoreCase(finalWorld)
                    )
            );
            if (!b) {
                return;
            }
        }
        invalidateCache(true);
        ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());
        plugin.getApiProvider().getEventFactory().handleNodeClear(this, before, after);
    }

    public void clearMetaKeys(String key, String server, String world, boolean temp) {
        String finalServer = Optional.ofNullable(server).orElse("global");
        String finalWorld = Optional.ofNullable(world).orElse("null");

        ImmutableSet<Node> before = ImmutableSet.copyOf(getNodes());
        synchronized (nodes) {
            boolean b = nodes.removeIf(n ->
                    n.isMeta() && (n.isTemporary() == temp) && n.getMeta().getKey().equalsIgnoreCase(key) &&
                            n.getServer().orElse("global").equalsIgnoreCase(finalServer) &&
                            n.getWorld().orElse("null").equalsIgnoreCase(finalWorld)
            );
            if (!b) {
                return;
            }
        }
        invalidateCache(true);
        ImmutableSet<Node> after = ImmutableSet.copyOf(getNodes());
        plugin.getApiProvider().getEventFactory().handleNodeClear(this, before, after);
    }

    public void clearTransientNodes() {
        ImmutableSet<Node> before = ImmutableSet.copyOf(getTransientNodes());

        synchronized (transientNodes) {
            transientNodes.clear();
        }
        invalidateCache(false);
        ImmutableSet<Node> after = ImmutableSet.copyOf(getTransientNodes());
        plugin.getApiProvider().getEventFactory().handleNodeClear(this, before, after);
    }

    /**
     * @return The temporary nodes held by the holder
     */
    public Set<Node> getTemporaryNodes() {
        return getPermissions(false).stream().filter(Node::isTemporary).collect(Collectors.toSet());
    }

    /**
     * @return The permanent nodes held by the holder
     */
    public Set<Node> getPermanentNodes() {
        return getPermissions(false).stream().filter(Node::isPermanent).collect(Collectors.toSet());
    }

    public Set<Node> getPrefixNodes() {
        return getPermissions(false).stream().filter(Node::isPrefix).collect(Collectors.toSet());
    }

    public Set<Node> getSuffixNodes() {
        return getPermissions(false).stream().filter(Node::isSuffix).collect(Collectors.toSet());
    }

    public Set<Node> getMetaNodes() {
        return getPermissions(false).stream().filter(Node::isMeta).collect(Collectors.toSet());
    }

    public OptionalInt getWeight() {
        if (this instanceof User) return OptionalInt.empty();

        OptionalInt weight = OptionalInt.empty();
        try {
            weight = getNodes().stream()
                    .filter(n -> n.getPermission().startsWith("weight."))
                    .map(n -> n.getPermission().substring("weight.".length()))
                    .mapToInt(Integer::parseInt)
                    .max();
        } catch (Exception ignored) {}

        if (!weight.isPresent()) {
            Integer w = plugin.getConfiguration().get(ConfigKeys.GROUP_WEIGHTS).get(getObjectName().toLowerCase());
            if (w != null) {
                weight = OptionalInt.of(w);
            }
        }

        return weight;
    }

    /**
     * Get a {@link List} of all of the groups the holder inherits, on all servers
     *
     * @return a {@link List} of group names
     */
    public List<String> getGroupNames() {
        return getNodes().stream()
                .filter(Node::isGroupNode)
                .map(Node::getGroupName)
                .collect(Collectors.toList());
    }

    public Set<HolderReference> getGroupReferences() {
        return getNodes().stream()
                .filter(Node::isGroupNode)
                .map(Node::getGroupName)
                .map(String::toLowerCase)
                .map(GroupReference::of)
                .collect(Collectors.toSet());
    }

    /**
     * Get a {@link List} of the groups the holder inherits on a specific server and world
     *
     * @param server the server to check
     * @param world  the world to check
     * @return a {@link List} of group names
     */
    public List<String> getLocalGroups(String server, String world) {
        return getNodes().stream()
                .filter(Node::isGroupNode)
                .filter(n -> n.shouldApplyOnWorld(world, false, true))
                .filter(n -> n.shouldApplyOnServer(server, false, true))
                .map(Node::getGroupName)
                .collect(Collectors.toList());
    }

    public List<String> getLocalGroups(String server, String world, boolean includeGlobal) {
        return getNodes().stream()
                .filter(Node::isGroupNode)
                .filter(n -> n.shouldApplyOnWorld(world, includeGlobal, true))
                .filter(n -> n.shouldApplyOnServer(server, includeGlobal, true))
                .map(Node::getGroupName)
                .collect(Collectors.toList());
    }

    /**
     * Get a {@link List} of the groups the holder inherits on a specific server
     *
     * @param server the server to check
     * @return a {@link List} of group names
     */
    public List<String> getLocalGroups(String server) {
        return getNodes().stream()
                .filter(Node::isGroupNode)
                .filter(n -> n.shouldApplyOnServer(server, false, true))
                .map(Node::getGroupName)
                .collect(Collectors.toList());
    }

    public static Map<String, Boolean> exportToLegacy(Set<Node> nodes) {
        Map<String, Boolean> m = new TreeMap<>((o1, o2) -> PriorityComparator.get().compareStrings(o1, o2));
        for (Node node : nodes) {
            m.put(node.toSerializedNode(), node.getValue());
        }
        return m;
    }
}
