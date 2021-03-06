/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.facebook.presto.spi;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class InMemoryRecordSet
        implements RecordSet
{
    private final List<ColumnType> types;
    private final Iterable<? extends List<?>> records;
    private final long totalBytes;

    public InMemoryRecordSet(Collection<ColumnType> types, Collection<? extends List<?>> records)
    {
        this.types = Collections.unmodifiableList(new ArrayList<>(types));
        this.records = records;

        long totalBytes = 0;
        for (List<?> record : records) {
            totalBytes += sizeOf(record);
        }
        this.totalBytes = totalBytes;
    }

    @Override
    public List<ColumnType> getColumnTypes()
    {
        return types;
    }

    @Override
    public RecordCursor cursor()
    {
        return new InMemoryRecordCursor(types, records.iterator(), totalBytes);
    }

    private static class InMemoryRecordCursor
            implements RecordCursor
    {
        private final List<ColumnType> types;
        private final Iterator<? extends List<?>> records;
        private final long totalBytes;
        private List<?> record;
        private long completedBytes;

        private InMemoryRecordCursor(List<ColumnType> types, Iterator<? extends List<?>> records, long totalBytes)
        {
            this.types = types;

            this.records = records;

            this.totalBytes = totalBytes;
        }

        @Override
        public long getTotalBytes()
        {
            return totalBytes;
        }

        @Override
        public long getCompletedBytes()
        {
            return completedBytes;
        }

        @Override
        public ColumnType getType(int field)
        {
            return types.get(field);
        }

        @Override
        public boolean advanceNextPosition()
        {
            if (!records.hasNext()) {
                record = null;
                return false;
            }
            record = records.next();
            completedBytes += sizeOf(record);

            return true;
        }

        @Override
        public boolean getBoolean(int field)
        {
            checkState(record != null, "no current record");
            checkNotNull(record.get(field), "value is null");
            return (Boolean) record.get(field);
        }

        @Override
        public long getLong(int field)
        {
            checkState(record != null, "no current record");
            checkNotNull(record.get(field), "value is null");
            return (Long) record.get(field);
        }

        @Override
        public double getDouble(int field)
        {
            checkState(record != null, "no current record");
            checkNotNull(record.get(field), "value is null");
            return (Double) record.get(field);
        }

        @Override
        public byte[] getString(int field)
        {
            checkState(record != null, "no current record");
            Object value = record.get(field);
            checkNotNull(value, "value is null");
            if (value instanceof byte[]) {
                return (byte[]) value;
            }
            if (value instanceof String) {
                return ((String) value).getBytes(StandardCharsets.UTF_8);
            }
            throw new IllegalArgumentException("Field " + field + " is not a String, but is a " + value.getClass().getName());
        }

        @Override
        public boolean isNull(int field)
        {
            checkState(record != null, "no current record");
            return record.get(field) == null;
        }

        @Override
        public void close()
        {
        }
    }

    public static Builder builder(TableMetadata tableMetadata)
    {
        return builder(tableMetadata.getColumns());
    }

    public static Builder builder(List<ColumnMetadata> columns)
    {
        List<ColumnType> columnTypes = new ArrayList<>();
        for (ColumnMetadata column : columns) {
            columnTypes.add(column.getType());
        }
        return builder(columnTypes);
    }

    public static Builder builder(Collection<ColumnType> columnsTypes)
    {
        return new Builder(columnsTypes);
    }

    public static class Builder
    {
        private final List<ColumnType> types;
        private final List<List<Object>> records = new ArrayList<>();

        private Builder(Collection<ColumnType> types)
        {
            checkNotNull(types, "types is null");
            this.types = Collections.unmodifiableList(new ArrayList<>(types));
            checkArgument(!this.types.isEmpty(), "types is empty");
        }

        public Builder addRow(Object... values)
        {
            checkNotNull(values, "values is null");
            checkArgument(values.length == types.size(), "Expected %s values in row, but got %s values", types.size(), values.length);
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                if (value == null) {
                    continue;
                }
                switch (types.get(i)) {
                    case BOOLEAN:
                        checkArgument(value instanceof Boolean, "Expected value %d to be an instance of Boolean, but is a %s", i, value.getClass().getSimpleName());
                        break;
                    case LONG:
                        checkArgument(value instanceof Long, "Expected value %d to be an instance of Long, but is a %s", i, value.getClass().getSimpleName());
                        break;
                    case DOUBLE:
                        checkArgument(value instanceof Double, "Expected value %d to be an instance of Double, but is a %s", i, value.getClass().getSimpleName());
                        break;
                    case STRING:
                        checkArgument(value instanceof String || value instanceof byte[],
                                "Expected value %d to be an instance of String or byte[], but is a %s", i, value.getClass().getSimpleName());
                        break;
                    default:
                        throw new IllegalStateException("Unsupported column type " + types.get(i));
                }
            }
            // Immutable list does not allow nulls
            records.add(Collections.unmodifiableList(new ArrayList<>(Arrays.asList(values))));
            return this;
        }

        public InMemoryRecordSet build()
        {
            return new InMemoryRecordSet(types, records);
        }
    }

    private static void checkArgument(boolean test, String message, Object... args)
    {
        if (!test) {
            throw new IllegalArgumentException(String.format(message, args));
        }
    }

    private static void checkNotNull(Object value, String message)
    {
        if (value == null) {
            throw new NullPointerException(message);
        }
    }

    private static void checkState(boolean test, String message)
    {
        if (!test) {
            throw new IllegalStateException(message);
        }
    }

    private static long sizeOf(List<?> record)
    {
        long completedBytes = 0;
        for (Object value : record) {
            if (value instanceof Boolean) {
                completedBytes++;
            }
            else if (value instanceof Long) {
                completedBytes += 8;
            }
            else if (value instanceof Double) {
                completedBytes += 8;
            }
            else if (value instanceof String) {
                completedBytes += ((String) value).length();
            }
            else if (value instanceof byte[]) {
                completedBytes += ((byte[]) value).length;
            }
        }
        return completedBytes;
    }
}
