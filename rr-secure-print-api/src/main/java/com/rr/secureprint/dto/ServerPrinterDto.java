package com.rr.secureprint.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ServerPrinterDto(
        @JsonAlias({"Name", "name"}) @JsonProperty("Name") String name,
        @JsonAlias({"DriverName", "driverName"}) @JsonProperty("DriverName") String driverName,
        @JsonAlias({"PortName", "portName"}) @JsonProperty("PortName") String portName,
        @JsonAlias({"Shared", "shared"}) @JsonProperty("Shared") Boolean shared,
        @JsonAlias({"ShareName", "shareName"}) @JsonProperty("ShareName") String shareName,
        @JsonAlias({"JobCount", "jobCount"}) @JsonProperty("JobCount") Integer jobCount
) {}
