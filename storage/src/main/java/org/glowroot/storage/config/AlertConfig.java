/*
 * Copyright 2015-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glowroot.storage.config;

import javax.annotation.Nullable;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ImmutableList;
import org.immutables.value.Value;

import org.glowroot.common.util.Versions;

@Value.Immutable
public abstract class AlertConfig {

    public abstract AlertKind kind();

    // transaction alert
    public abstract String transactionType();
    public abstract @Nullable Double transactionPercentile();
    public abstract @Nullable Integer transactionThresholdMillis();
    public abstract @Nullable Integer minTransactionCount();

    // gauge alert
    public abstract String gaugeName();
    public abstract @Nullable Double gaugeThreshold();

    // both
    public abstract int timePeriodSeconds();

    public abstract ImmutableList<String> emailAddresses();

    @Value.Derived
    @JsonIgnore
    public String version() {
        return Versions.getJsonVersion(this);
    }

    public static enum AlertKind {
        TRANSACTION, GAUGE
    }
}
