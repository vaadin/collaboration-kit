/*
 * Copyright 2000-2024 Vaadin Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.vaadin.collaborationengine;

import java.io.Serializable;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.vaadin.collaborationengine.CollaborationAvatarGroup.ImageHandler;

/**
 * User information of a collaborating user, used with various features of the
 * collaboration engine.
 *
 * @author Vaadin Ltd
 * @since 1.0
 */
public class UserInfo implements Serializable {

    private String id;
    private String name;
    private String abbreviation;
    private String image;
    private int colorIndex;

    /**
     * Creates a new user info object from the given user id.
     *
     * @param userId
     *            the user id, not {@code null}
     *
     * @since 1.0
     */
    @JsonCreator
    public UserInfo(@JsonProperty("id") String userId) {
        this(userId, -1);
    }

    /**
     * Creates a new user info object from the given user id and name.
     *
     * @param userId
     *            the user id, not {@code null}
     * @param name
     *            the name of the user
     *
     * @since 1.0
     */
    public UserInfo(String userId, String name) {
        this(userId);
        this.name = name;
    }

    /**
     * Creates a new user info object from the given user id, name and image
     * URL. The color index is calculated based on the id. All other properties
     * are left empty.
     * <p>
     * If this user info is given to a {@link CollaborationAvatarGroup}, the
     * image URL is used to load the user's avatar. Alternatively, the user
     * images can be loaded from a backend to the avatar group with
     * {@link CollaborationAvatarGroup#setImageHandler(ImageHandler)}.
     *
     *
     * @param userId
     *            the user id, not {@code null}
     * @param name
     *            the name of the user
     * @param imageUrl
     *            the URL of the user image
     *
     * @since 1.0
     */
    public UserInfo(String userId, String name, String imageUrl) {
        this(userId, name);
        this.image = imageUrl;
    }

    /*
     * This constructor is for SystemUserInfo so that userColors in CE won't be
     * messed up by this user.
     */
    UserInfo(String userId, int colorIndex) {
        Objects.requireNonNull(userId, "Null user id isn't supported");
        this.id = userId;
        this.colorIndex = colorIndex;
    }

    /**
     * Gets the user's unique identifier.
     *
     * @return the user's id, not {@code null}
     *
     * @since 1.0
     */
    public String getId() {
        return id;
    }

    /**
     * Gets the user's name.
     *
     * @return the user's name.
     *
     * @since 1.0
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the user's name.
     *
     * @param name
     *            the name to set
     *
     * @since 1.0
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Gets the user's abbreviation.
     * <p>
     * Note: This is not computed based on the user's name, but needs to be
     * explicitly set with {@link #setAbbreviation(String)}.
     *
     * @return the user's abbreviation
     *
     * @since 1.0
     */
    public String getAbbreviation() {
        return abbreviation;
    }

    /**
     * Sets the user's abbreviation.
     *
     * @param abbreviation
     *            the abbreviation to set
     *
     * @since 1.0
     */
    public void setAbbreviation(String abbreviation) {
        this.abbreviation = abbreviation;
    }

    /**
     * Gets the url of the user's image.
     *
     * @return the image url
     *
     * @since 1.0
     */
    public String getImage() {
        return image;
    }

    /**
     * Sets the url of the user's image.
     * <p>
     * If this user info is given to a {@link CollaborationAvatarGroup}, the
     * image URL is used to load the user's avatar. Alternatively, the user
     * images can be loaded from a backend to the avatar group with
     * {@link CollaborationAvatarGroup#setImageHandler(ImageHandler)}.
     *
     * @param imageUrl
     *            the image URL to set
     *
     * @since 1.0
     */
    public void setImage(String imageUrl) {
        this.image = imageUrl;
    }

    /**
     * Gets the user's color index.
     * <p>
     * The color index defines the user specific color. In practice, color index
     * {@code n} means that the user color will be set as the CSS variable
     * {@code --vaadin-user-color-n}.
     * <p>
     * The default value is -1, which indicates that the user color can be
     * automatically assigned by Collaboration Engine.
     *
     * @return the user's color index
     *
     * @since 1.0
     */
    public int getColorIndex() {
        return colorIndex;
    }

    /**
     * Sets the user's color index.
     * <p>
     * Setting it to -1 (which is the default value) indicates that the user
     * color can be automatically assigned by Collaboration Engine.
     *
     * @param colorIndex
     *            the color index to set
     * @see #getColorIndex()
     *
     * @since 1.0
     */
    public void setColorIndex(int colorIndex) {
        this.colorIndex = colorIndex;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        UserInfo that = (UserInfo) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
