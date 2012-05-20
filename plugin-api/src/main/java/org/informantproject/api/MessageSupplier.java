/**
 * Copyright 2012 the original author or authors.
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
package org.informantproject.api;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
public abstract class MessageSupplier {

    public static Supplier<Message> of(String message) {
        return Supplier.of(Message.of(message));
    }

    public static Supplier<Message> of(String template, Object... args) {
        return Supplier.of(Message.of(template, args));
    }

    public static Supplier<Message> withContext(String message, ContextMap context) {
        return Supplier.of(Message.of(message, context));
    }

    public static Supplier<Message> withContextMap(String template, Object[] args,
            ContextMap context) {

        return Supplier.of(Message.of(template, args, context));
    }
}