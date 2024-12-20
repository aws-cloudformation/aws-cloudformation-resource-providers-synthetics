package com.amazon.synthetics.canary;
 
import org.junit.jupiter.api.Test;
 
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
 
public class ModelHelperTest {
 
    @Test
    public void buildTagDiff_canAddNewTags() {
        List<Tag> requestedTags = new ArrayList<>();
        Map<String, String> existingTags = new HashMap<>();
 
        requestedTags.add(new Tag("key1", "value1"));
        requestedTags.add(new Tag("key2", "value2"));
 
        Map<String, Map<String, String>> tagDiff = ModelHelper.buildTagDiff(requestedTags, existingTags);
 
        Map<String, String> expectedAddedTags = new HashMap<>();
        expectedAddedTags.put("key1", "value1");
        expectedAddedTags.put("key2", "value2");
 
        assert tagDiff.get(ModelHelper.ADD_TAGS).equals(expectedAddedTags);
        assert tagDiff.get(ModelHelper.REMOVE_TAGS).isEmpty();
    }
 
    @Test
    public void buildTagDiff_updateExistingTagKeys_willUpdateAndRemoveTags() {
        List<Tag> requestedTags = new ArrayList<>();
        Map<String, String> existingTags = new HashMap<>();
 
        requestedTags.add(new Tag("key1", "value1"));
        requestedTags.add(new Tag("key3", "value2"));
 
        existingTags.put("key1", "value1");
        existingTags.put("key2", "value2");
 
        Map<String, Map<String, String>> tagDiff = ModelHelper.buildTagDiff(requestedTags, existingTags);
 
        Map<String, String> expectedUpdatedTags = new HashMap<>();
        expectedUpdatedTags.put("key3", "value2");
 
        Map<String, String> expectedRemovedTags = new HashMap<>();
        expectedRemovedTags.put("key2", "value2");
 
        assert tagDiff.get(ModelHelper.ADD_TAGS).equals(expectedUpdatedTags);
        assert tagDiff.get(ModelHelper.REMOVE_TAGS).equals(expectedRemovedTags);
    }
 
    @Test
    public void buildTagDiff_updatingExistingTagValues_willNotRemoveTags() {
        List<Tag> requestedTags = new ArrayList<>();
        Map<String, String> existingTags = new HashMap<>();
 
        requestedTags.add(new Tag("key1", "value1"));
        requestedTags.add(new Tag("key2", "value3"));
 
        existingTags.put("key1", "value1");
        existingTags.put("key2", "value2");
 
        Map<String, Map<String, String>> tagDiff = ModelHelper.buildTagDiff(requestedTags, existingTags);
 
        Map<String, String> expectedUpdatedTags = new HashMap<>();
        expectedUpdatedTags.put("key2", "value3");
 
        assert tagDiff.get(ModelHelper.ADD_TAGS).equals(expectedUpdatedTags);
        assert tagDiff.get(ModelHelper.REMOVE_TAGS).isEmpty();
    }
 
    @Test
    public void buildTagDiff_canRemoveExistingTags() {
        List<Tag> requestedTags = new ArrayList<>();
        Map<String, String> existingTags = new HashMap<>();
 
        requestedTags.add(new Tag("key1", "value1"));
 
        existingTags.put("key1", "value1");
        existingTags.put("key2", "value2");
 
        Map<String, Map<String, String>> tagDiff = ModelHelper.buildTagDiff(requestedTags, existingTags);
 
        Map<String, String> expectedRemovedTags = new HashMap<>();
        expectedRemovedTags.put("key2", "value2");
 
        assert tagDiff.get(ModelHelper.ADD_TAGS).isEmpty();
        assert tagDiff.get(ModelHelper.REMOVE_TAGS).equals(expectedRemovedTags);
    }
 
    @Test
    public void buildTagDiff_nullRequestedTags_willRemoveAllExistingTags() {
        Map<String, String> existingTags = new HashMap<>();
 
        existingTags.put("key1", "value1");
        existingTags.put("key2", "value2");
 
        Map<String, Map<String, String>> tagDiff = ModelHelper.buildTagDiff(null, existingTags);
 
        assert tagDiff.get(ModelHelper.ADD_TAGS).isEmpty();
        assert tagDiff.get(ModelHelper.REMOVE_TAGS).equals(existingTags);
    }
 
    @Test
    public void buildTagDiff_emptyRequestedTags_willRemoveAllExistingTags() {
        List<Tag> requestedTags = new ArrayList<>();
        Map<String, String> existingTags = new HashMap<>();
 
        existingTags.put("key1", "value1");
        existingTags.put("key2", "value2");
 
        Map<String, Map<String, String>> tagDiff = ModelHelper.buildTagDiff(requestedTags, existingTags);
 
        assert tagDiff.get(ModelHelper.ADD_TAGS).isEmpty();
        assert tagDiff.get(ModelHelper.REMOVE_TAGS).equals(existingTags);
    }
}