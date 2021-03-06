/*
 *  Copyright (C) 2020 Temporal Technologies, Inc. All Rights Reserved.
 *
 *  Copyright 2012-2016 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 *  Modifications copyright (C) 2017 Uber Technologies, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"). You may not
 *  use this file except in compliance with the License. A copy of the License is
 *  located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 *  or in the "license" file accompanying this file. This file is distributed on
 *  an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 *  express or implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 */

package io.temporal.common.converter;

import com.google.common.base.Defaults;
import io.temporal.proto.common.Payload;
import io.temporal.proto.common.Payloads;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Optional;

/**
 * Implements conversion through GSON JSON processor. To extend use {@link
 * #GsonJsonDataConverter(PayloadConverter)} constructor.
 *
 * @author fateev
 */
public final class GsonJsonDataConverter implements DataConverter {

  private static final DataConverter INSTANCE = new GsonJsonDataConverter();
  private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
  private final PayloadConverter converter;

  public static DataConverter getInstance() {
    return INSTANCE;
  }

  private GsonJsonDataConverter() {
    this(GsonJsonPayloadConverter.getInstance());
  }

  public GsonJsonDataConverter(PayloadConverter converter) {
    this.converter = converter;
  }

  @Override
  public PayloadConverter getPayloadConverter() {
    return converter;
  }

  /**
   * When values is empty or it contains a single value and it is null then return empty blob. If a
   * single value do not wrap it into Json array. Exception stack traces are converted to a single
   * string stack trace to save space and make them more readable.
   *
   * @return serialized values
   */
  @Override
  public Optional<Payloads> toData(Object... values) throws DataConverterException {
    if (values == null || values.length == 0) {
      return Optional.empty();
    }
    try {
      Payloads.Builder result = Payloads.newBuilder();
      for (Object value : values) {
        Optional<Payload> payload = converter.toData(value);
        if (payload.isPresent()) {
          result.addPayloads(payload.get());
        } else {
          result.addPayloads(Payload.getDefaultInstance());
        }
      }
      return Optional.of(result.build());
    } catch (DataConverterException e) {
      throw e;
    } catch (Throwable e) {
      throw new DataConverterException(e);
    }
  }

  @Override
  public <T> T fromData(Optional<Payloads> content, Class<T> valueClass, Type valueType)
      throws DataConverterException {
    if (!content.isPresent()) {
      return null;
    }
    Payloads c = content.get();
    if (c.getPayloadsCount() == 0) {
      return null;
    }
    if (c.getPayloadsCount() != 1) {
      throw new DataConverterException(
          "Found multiple payloads while a single one expected", content, valueType);
    }
    return converter.fromData(c.getPayloads(0), valueClass, valueType);
  }

  @Override
  public Object[] fromDataArray(
      Optional<Payloads> content, Class<?>[] parameterTypes, Type[] valueTypes)
      throws DataConverterException {
    try {
      if (parameterTypes != null
          && (valueTypes == null || parameterTypes.length != valueTypes.length)) {
        throw new IllegalArgumentException(
            "parameterTypes don't match length of valueTypes: "
                + Arrays.toString(parameterTypes)
                + "<>"
                + Arrays.toString(valueTypes));
      }
      if (!content.isPresent()) {
        if (valueTypes.length == 0) {
          return EMPTY_OBJECT_ARRAY;
        } else {
          throw new DataConverterException("Empty content", content, valueTypes);
        }
      }
      Payloads c = content.get();
      int count = c.getPayloadsCount();
      int length = valueTypes.length;
      Object[] result = new Object[length];
      for (int i = 0; i < length; i++) {
        Type vt = valueTypes[i];
        Class<?> pt = parameterTypes[i];
        if (i >= count) {
          result[i] = Defaults.defaultValue((Class<?>) vt);
        } else {
          result[i] = converter.fromData(c.getPayloads(i), pt, vt);
        }
      }
      return result;
    } catch (DataConverterException e) {
      throw e;
    } catch (Throwable e) {
      throw new DataConverterException(e);
    }
  }
}
