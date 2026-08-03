package org.j2os.examples.web.api;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.j2os.examples.web.common.handler.ErrorHandler;
import org.j2os.examples.web.entity.tree.Tree;
import org.j2os.examples.web.repository.TreeRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Simple tree/category CRUD API backed by {@link TreeRepository}, used to drive a lazy-loading
 * tree UI widget: each node reports whether it has children ({@code NODE_HAS_CHILDREN}) without
 * the client having to fetch the whole subtree up front.
 */
@RestController
@RequiredArgsConstructor
public class TreeAPI {

    private final TreeRepository treeRepository;

    /**
     * Returns the direct children of a node (or the root nodes, if {@code parentId} is omitted),
     * each flagged with whether it has children of its own.
     *
     * @param parentId the parent node's id, or {@code null}/omitted for the root level
     * @param response the HTTP response, passed to {@link ErrorHandler} for error reporting
     * @return a list of {@code NODE_ID}/{@code NODE_NAME}/{@code NODE_HAS_CHILDREN} maps, or
     *         whatever {@link ErrorHandler#getMessage} returns on failure
     */
    @GetMapping("/getTree")
    public Object getCategory(@RequestParam(value = "NODE_PARENT_ID", required = false) String parentId, HttpServletResponse response) {
        try {
            List<Tree> categories = treeRepository.findAllByParentTree_TreeId(Objects.nonNull(parentId) ? Integer.valueOf(parentId) : null);
            List<Map<String, Object>> result = new ArrayList<>();
            for (Tree child : categories) {
                boolean hasChildren = treeRepository.existsByParentTree_TreeId(child.getTreeId());
                Map<String, Object> map = new HashMap<>();
                map.put("NODE_ID", child.getTreeId());
                map.put("NODE_NAME", child.getTreeName());
                map.put("NODE_HAS_CHILDREN", hasChildren);
                result.add(map);
            }
            return result;
        } catch (Exception e) {
            return ErrorHandler.getMessage(e, response);
        }
    }

    /**
     * Creates a new child node under the given parent.
     *
     * @param parentId the id of the node to create a child under
     * @param response the HTTP response, passed to {@link ErrorHandler} for error reporting
     * @return the newly created {@link Tree} node, or whatever {@link ErrorHandler#getMessage}
     *         returns on failure (including when {@code parentId} doesn't exist)
     */
    @PostMapping("/saveTree")
    public Object saveCategory(@RequestParam("NODE_PARENT_ID") String parentId, HttpServletResponse response) {
        try {
            var parent = treeRepository.findById(Integer.parseInt(parentId)).orElseThrow(Exception::new);
            Tree child = new Tree();
            child.setParentTree(parent);
            child.setTreeName("فرزند جدید");
            return treeRepository.save(child);
        } catch (Exception e) {
            return ErrorHandler.getMessage(e, response);
        }
    }

    /**
     * Deletes a node and reports its former parent's id, so the client knows which part of the
     * tree to refresh.
     *
     * @param nodeId   the id of the node to delete
     * @param response the HTTP response, passed to {@link ErrorHandler} for error reporting
     * @return {@code {"NODE_PARENT_ID": <the deleted node's former parent id>}}, or whatever
     *         {@link ErrorHandler#getMessage} returns on failure
     */
    @PostMapping("/removeTree")
    public Object removeCategory(@RequestParam("NODE_ID") String nodeId, HttpServletResponse response) {
        try {
            var tree = treeRepository.findById(Integer.parseInt(nodeId)).orElseThrow(Exception::new);
            var parentId = tree.getParentTree().getTreeId();
            treeRepository.delete(tree);
            return Map.of("NODE_PARENT_ID", parentId);
        } catch (Exception e) {
            return ErrorHandler.getMessage(e, response);
        }
    }

    // Reference for a rename/update endpoint, following the same shape as saveTree/removeTree -
    // intentionally not wired up (no @PostMapping) since renaming isn't exposed yet.
    /*
    public Object updateCategory(Tree tree, HttpServletResponse response) {
        try {
            tree = treeRepository.findById(tree.getTreeId()).orElseThrow(Exception::new);
            var parentId = tree.getParentTree().getTreeId();
            tree.setTreeName("ویرایش شده");
            treeRepository.save(tree);
            return Map.of("NODE_PARENT_ID", parentId);
        } catch (Exception e) {
            return ErrorHandler.getMessage(e, response);
        }
    }
    */
}