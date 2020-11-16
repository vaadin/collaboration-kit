/*
 * Copyright (C) 2020 Vaadin Ltd
 *
 * This program is available under Commercial Vaadin Runtime License 1.0
 * (CVRLv1).
 *
 * For the full License, see http://vaadin.com/license/cvrl-1
 */
package com.vaadin.collaborationengine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Base64;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.vaadin.collaborationengine.LicenseHandler.LicenseInfo;
import com.vaadin.collaborationengine.LicenseHandler.LicenseInfoWrapper;
import com.vaadin.collaborationengine.LicenseHandler.StatisticsInfo;
import com.vaadin.flow.internal.MessageDigestUtil;

class FileHandler {

    static final String DATA_DIR_CONFIG_PROPERTY = "ce.dataDir";

    private static Supplier<Path> dataDirPathSupplier;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Path statsFilePath;
    private final Path licenseFilePath;

    FileHandler() {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.setVisibility(PropertyAccessor.FIELD,
                Visibility.NON_PRIVATE);
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd");
        objectMapper.setDateFormat(df);

        if (dataDirPathSupplier == null) {
            throw createServiceInitNotCalledException();
        }
        Path path = dataDirPathSupplier.get();
        if (path == null) {
            throw createDataDirNotConfiguredException();
        }
        statsFilePath = createStatsFilePath(path);
        licenseFilePath = createLicenseFilePath(path);
    }

    static Path createStatsFilePath(Path dirPath) {
        return Paths.get(dirPath.toString(), "ce-statistics.json");
    }

    static Path createLicenseFilePath(Path dirPath) {
        return Paths.get(dirPath.toString(), "ce-license.json");
    }

    static void setDataDirectorySupplier(Supplier<Path> dataDirectorySupplier) {
        dataDirPathSupplier = dataDirectorySupplier;
    }

    void writeStats(StatisticsInfo stats) {
        try {
            objectMapper.writeValue(statsFilePath.toFile(), stats);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Collaboration Engine wasn't able to write statistics into file at '"
                            + statsFilePath
                            + "'. Check that the file is readable by the app, and not locked.",
                    e);
        }
    }

    LicenseInfo readLicenseFile() {
        try {
            JsonNode licenseJson = readFileAsJson(licenseFilePath)
                    .orElseThrow(this::createLicenseNotFoundException);

            LicenseInfoWrapper licenseInfoWrapper = objectMapper
                    .treeToValue(licenseJson, LicenseInfoWrapper.class);

            String calculatedChecksum = Base64.getEncoder()
                    .encodeToString(MessageDigestUtil.sha256(objectMapper
                            .writeValueAsString(licenseJson.get("content"))));

            if (licenseInfoWrapper.checksum == null
                    || !licenseInfoWrapper.checksum
                            .equals(calculatedChecksum)) {
                throw createLicenseInvalidException(null);
            }

            return licenseInfoWrapper.content;

        } catch (JsonProcessingException e) {
            throw createLicenseInvalidException(e);
        }
    }

    StatisticsInfo readStatsFile() {
        try {
            Optional<JsonNode> statsJson = readFileAsJson(statsFilePath);
            if (statsJson.isPresent()) {
                return objectMapper.treeToValue(statsJson.get(),
                        StatisticsInfo.class);
            } else {
                return new StatisticsInfo(Collections.emptyMap(), null);
            }
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Collaboration Engine failed to parse the statistics information from file '"
                            + licenseFilePath + "'.",
                    e);
        }
    }

    private Optional<JsonNode> readFileAsJson(Path filePath)
            throws JsonProcessingException {
        try {
            File file = filePath.toFile();
            if (!file.exists()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readTree(file));
        } catch (JsonProcessingException e) {
            throw e;
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Collaboration Engine wasn't able to read the file at '"
                            + filePath
                            + "'. Check that the file is readable by the app, and not locked.",
                    e);
        }
    }

    private RuntimeException createServiceInitNotCalledException() {
        return new IllegalStateException(
                "Collaboration Engine is missing required configuration "
                        + "that should be provided by a VaadinServiceInitListener. "
                        + "Collaboration Engine is supported only in a Vaadin application, "
                        + "where VaadinService initialization is expected to happen before usage.");
    }

    private RuntimeException createDataDirNotConfiguredException() {
        return new IllegalStateException(
                "Missing required configuration property '"
                        + DATA_DIR_CONFIG_PROPERTY
                        + "'. Using Collaboration Engine in production requires having a valid license file "
                        + "and configuring the directory where that file is stored e.g. as a system property. "
                        + "Instructions can be found in the Vaadin documentation.");
    }

    private RuntimeException createLicenseNotFoundException() {
        return new IllegalStateException(
                "Collaboration Engine failed to find the license file at '"
                        + licenseFilePath
                        + ". Using Collaboration Engine in production requires a valid license file. "
                        + "Instructions for obtaining a license can be found in the Vaadin documentation. "
                        + "If you already have a license, make sure that the '"
                        + DATA_DIR_CONFIG_PROPERTY
                        + "' property is pointing to the correct directory "
                        + "and that the directory contains the license file.");
    }

    private RuntimeException createLicenseInvalidException(Throwable cause) {
        return new IllegalStateException(
                "Collaboration Engine failed to parse the file '"
                        + licenseFilePath
                        + "'. The content of the license file is not valid. "
                        + "If you have made any changes to the file, please revert those changes. "
                        + "If that's not possible, contact Vaadin to get a new copy of the license file.",
                cause);
    }
}