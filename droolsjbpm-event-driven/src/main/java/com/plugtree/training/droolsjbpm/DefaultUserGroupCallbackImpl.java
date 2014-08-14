package com.plugtree.training.droolsjbpm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jbpm.services.task.impl.model.GroupImpl;
import org.jbpm.services.task.impl.model.UserImpl;
import org.kie.internal.task.api.UserGroupCallback;

public class DefaultUserGroupCallbackImpl implements UserGroupCallback {

	private final Map<UserImpl, List<GroupImpl>> userGroupMapping = new HashMap<UserImpl, List<GroupImpl>>();
	private final Set<String> groupNames = new HashSet<String>();

    public DefaultUserGroupCallbackImpl() {
    }

    public void addUser(String userId, String... groupIds) {
    	UserImpl user = new UserImpl(userId);
    	List<GroupImpl> groups = new ArrayList<GroupImpl>();
    	if (groupIds != null) {
    		for (String groupId : groupIds) {
    			groups.add(new GroupImpl(groupId));
    		}
    	}
    	userGroupMapping.put(user, groups);
    	groupNames.addAll(Arrays.asList(groupIds));
    }
    
    public void removeUser(String userId) {
    	userGroupMapping.remove(new UserImpl(userId));
    }
    
    @Override
    public boolean existsUser(String userId) {
        Iterator<UserImpl> iter = userGroupMapping.keySet().iterator();
        while (iter.hasNext()) {
            UserImpl u = iter.next();
            if (u.getId().equals(userId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean existsGroup(String groupId) {
    	return groupNames.contains(groupId);
    }

    public List<String> getGroupsForUser(String userId, List<String> groupIds) {
        return getGroupsForUser(userId);
    }

    public List<String> getGroupsForUser(String userId) {
        Iterator<UserImpl> iter = userGroupMapping.keySet().iterator();
        while (iter.hasNext()) {
            UserImpl u = iter.next();
            if (u.getId().equals(userId)) {
                List<String> groupList = new ArrayList<String>();
                List<GroupImpl> userGroupList = userGroupMapping.get(u);
                for (GroupImpl g : userGroupList) {
                    groupList.add(g.getId());
                }
                return groupList;
            }
        }
        return null;
    }

    @Override
    public List<String> getGroupsForUser(String userId, List<String> groupIds,
            List<String> allExistingGroupIds) {
        return getGroupsForUser(userId);
    }
    
    public Map<UserImpl, List<GroupImpl>> getUserGroupMapping() {
    	Map<UserImpl, List<GroupImpl>> result = new HashMap<UserImpl, List<GroupImpl>>();
    	result.putAll(userGroupMapping);
		return result;
	}
}
