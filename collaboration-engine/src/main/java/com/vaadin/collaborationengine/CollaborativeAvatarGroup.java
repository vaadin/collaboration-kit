/*
 * Copyright (C) 2020 Vaadin Ltd
 *
 * This program is available under Commercial Vaadin Add-On License 3.0
 * (CVALv3).
 *
 * See the file licensing.txt distributed with this software for more
 * information about licensing.
 *
 * You should have received a copy of the license along with this program.
 * If not, see <http://vaadin.com/license/cval-3>.
 */
package com.vaadin.collaborationengine;

import com.vaadin.flow.component.HasTheme;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.vaadin.flow.component.Composite;
import com.vaadin.flow.component.HasSize;
import com.vaadin.flow.component.HasStyle;
import com.vaadin.flow.component.avatar.AvatarGroup;
import com.vaadin.flow.component.avatar.AvatarGroup.AvatarGroupItem;
import com.vaadin.flow.component.avatar.AvatarGroupVariant;
import com.vaadin.flow.function.SerializableFunction;
import com.vaadin.flow.shared.Registration;

/**
 * Extension of the {@link AvatarGroup} component which integrates with the
 * {@link CollaborationEngine}. It updates the avatars in real time based on the
 * other attached avatar groups connected to the same topic.
 *
 * @author Vaadin Ltd
 */
public class CollaborativeAvatarGroup extends Composite<AvatarGroup>
        implements HasSize, HasStyle, HasTheme {

    static final String MAP_NAME = CollaborativeAvatarGroup.class.getName();
    static final String KEY = "users";

    private final UserInfo localUser;

    /**
     * Creates a new collaborative avatar group component with the provided
     * local user and topic id.
     * <p>
     * The provided user information is used in the local user's avatar which is
     * displayed to the other users.
     * <p>
     * Whenever another collaborative avatar group with the same topic id is
     * attached to another user's UI, this avatar group is updated to include an
     * avatar with that user's information.
     *
     * @param localUser
     *            the information of the local user
     * @param topicId
     *            the id of the topic to connect to
     */
    public CollaborativeAvatarGroup(UserInfo localUser, String topicId) {
        this.localUser = Objects.requireNonNull(localUser,
                "User cannot be null");
        CollaborationEngine.getInstance().openTopicConnection(getContent(),
                topicId, this::onConnectionActivate);
    }

    /**
     * Gets the maximum number of avatars to display, or {@code null} if no max
     * has been set.
     *
     * @return the max number of avatars
     * @see AvatarGroup#getMax()
     */
    public Integer getMax() {
        return getContent().getMax();
    }

    /**
     * Sets the the maximum number of avatars to display.
     * <p>
     * By default, all the avatars are displayed. When max is set, the
     * overflowing avatars are grouped into one avatar.
     *
     * @param max
     *            the max number of avatars, or {@code null} to remove the max
     * @see AvatarGroup#setMax(Integer)
     */
    public void setMax(Integer max) {
        getContent().setMax(max);
    }

    /**
     * Adds theme variants to the avatar group component.
     *
     * @param variants
     *            theme variants to add
     * @see AvatarGroup#addThemeVariants(AvatarGroupVariant...)
     */
    public void addThemeVariants(AvatarGroupVariant... variants) {
        getContent().addThemeVariants(variants);
    }

    /**
     * Removes theme variants from the avatar group component.
     *
     * @param variants
     *            theme variants to remove
     * @see AvatarGroup#removeThemeVariants(AvatarGroupVariant...)
     */
    public void removeThemeVariants(AvatarGroupVariant... variants) {
        getContent().removeThemeVariants(variants);
    }

    private Registration onConnectionActivate(TopicConnection topicConnection) {
        CollaborativeMap map = topicConnection.getNamedMap(MAP_NAME);

        updateUsers(map,
                oldValue -> Stream.concat(oldValue, Stream.of(localUser)));

        map.subscribe(this::onMapChange);

        return () -> onConnectionDeactivate(map);
    }

    private void onConnectionDeactivate(CollaborativeMap map) {
        updateUsers(map, oldValue -> oldValue.filter(this::isNotLocalUser));
        getContent().setItems(Collections.emptyList());
    }

    private void updateUsers(CollaborativeMap map,
            SerializableFunction<Stream<UserInfo>, Stream<UserInfo>> updater) {
        while (true) {
            List<UserInfo> oldValue = (List<UserInfo>) map.get(KEY);
            List<UserInfo> newValue = updater.apply(
                    oldValue != null ? oldValue.stream() : Stream.empty())
                    .collect(Collectors.toList());
            if (map.replace(KEY, oldValue, newValue)) {
                break;
            }
        }
    }

    private void onMapChange(MapChangeEvent event) {
        List<UserInfo> users = (List<UserInfo>) event.getValue();
        List<AvatarGroupItem> items = users != null ? users.stream()
                .filter(this::isNotLocalUser).map(this::userToAvatarGroupItem)
                .collect(Collectors.toList()) : Collections.emptyList();
        getContent().setItems(items);
    }

    private AvatarGroupItem userToAvatarGroupItem(UserInfo user) {
        AvatarGroupItem item = new AvatarGroupItem();
        item.setName(user.getName());
        item.setAbbreviation(user.getAbbreviation());
        item.setImage(user.getImage());
        item.setColorIndex(user.getColorIndex());
        return item;
    }

    private boolean isNotLocalUser(UserInfo user) {
        return !localUser.equals(user);
    }
}