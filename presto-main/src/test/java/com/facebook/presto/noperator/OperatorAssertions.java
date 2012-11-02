/*
 * Copyright 2004-present Facebook. All Rights Reserved.
 */
package com.facebook.presto.noperator;

import com.facebook.presto.nblock.Block;
import com.facebook.presto.nblock.BlockIterable;
import com.facebook.presto.nblock.BlockIterables;
import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.List;

import static com.facebook.presto.nblock.BlockAssertions.assertBlocksEquals;
import static org.testng.Assert.assertEquals;

public final class OperatorAssertions
{
    private OperatorAssertions()
    {
    }

    public static void assertOperatorEquals(Operator actual, Operator expected)
    {
        assertEquals(actual.getChannelCount(), expected.getChannelCount(), "Channel count");

        List<BlockIterable> actualColumns = loadColumns(actual);
        List<BlockIterable> expectedColumns = loadColumns(expected);
        for (int i = 0; i < actualColumns.size(); i++) {
            BlockIterable actualColumn = actualColumns.get(i);
            BlockIterable expectedColumn = expectedColumns.get(i);
            assertBlocksEquals(actualColumn, expectedColumn);
        }
    }

    public static List<BlockIterable> loadColumns(Operator operator)
    {
        List<ImmutableList.Builder<Block>> blockBuilders = new ArrayList<>();
        for (int i = 0; i < operator.getChannelCount(); i++) {
            blockBuilders.add(ImmutableList.<Block>builder());
        }
        for (Page page : operator) {
            Block[] blocks = page.getBlocks();
            for (int i = 0; i < blocks.length; i++) {
                blockBuilders.get(i).add(blocks[i]);
            }
        }

        ImmutableList.Builder<BlockIterable> blockIterables = ImmutableList.builder();
        for (ImmutableList.Builder<Block> blockBuilder : blockBuilders) {
            blockIterables.add(BlockIterables.createBlockIterable(blockBuilder.build()));
        }
        return blockIterables.build();
    }
}