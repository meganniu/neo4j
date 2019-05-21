/*
 * Copyright (c) 2002-2019 "Neo4j,"
 * Neo4j Sweden AB [http://neo4j.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.kernel.impl.index.schema.fusion;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

import org.neo4j.internal.kernel.api.InternalIndexState;
import org.neo4j.internal.kernel.api.exceptions.schema.MisconfiguredIndexException;
import org.neo4j.internal.schema.IndexProviderDescriptor;
import org.neo4j.io.fs.FileSystemAbstraction;
import org.neo4j.kernel.api.index.IndexProvider;
import org.neo4j.kernel.api.index.IndexSample;
import org.neo4j.kernel.impl.index.schema.GenericNativeIndexProvider;
import org.neo4j.kernel.impl.index.schema.IndexDescriptor;
import org.neo4j.kernel.impl.index.schema.IndexDescriptorFactory;
import org.neo4j.kernel.impl.index.schema.StoreIndexDescriptor;
import org.neo4j.test.rule.RandomRule;
import org.neo4j.values.storable.Value;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.neo4j.internal.helpers.ArrayUtil.array;
import static org.neo4j.internal.schema.SchemaDescriptor.forLabel;
import static org.neo4j.kernel.api.index.IndexDirectoryStructure.NONE;
import static org.neo4j.kernel.impl.api.index.TestIndexProviderDescriptor.PROVIDER_DESCRIPTOR;
import static org.neo4j.kernel.impl.index.schema.fusion.FusionIndexBase.CATEGORY_OF;
import static org.neo4j.kernel.impl.index.schema.fusion.FusionIndexTestHelp.fill;
import static org.neo4j.kernel.impl.index.schema.fusion.FusionVersion.v30;
import static org.neo4j.kernel.impl.index.schema.fusion.IndexSlot.GENERIC;
import static org.neo4j.kernel.impl.index.schema.fusion.IndexSlot.LUCENE;

@RunWith( Parameterized.class )
public class FusionIndexProviderTest
{
    private static final IndexProviderDescriptor DESCRIPTOR = new IndexProviderDescriptor( "test-fusion", "1" );
    public static final StoreIndexDescriptor AN_INDEX =
            IndexDescriptorFactory.forSchema( forLabel( 0, 0 ), PROVIDER_DESCRIPTOR ).withId( 0 );

    private EnumMap<IndexSlot,IndexProvider> providers;
    private IndexProvider[] aliveProviders;
    private IndexProvider fusionIndexProvider;
    private SlotSelector slotSelector;
    private InstanceSelector<IndexProvider> instanceSelector;

    @Parameterized.Parameters( name = "{0}" )
    public static FusionVersion[] versions()
    {
        return new FusionVersion[]
                {
                        v30
                };
    }

    @Parameterized.Parameter
    public static FusionVersion fusionVersion;

    @Before
    public void setup()
    {
        slotSelector = fusionVersion.slotSelector();
        setupMocks();
    }

    @Rule
    public RandomRule random = new RandomRule();

    private void setupMocks()
    {
        IndexSlot[] aliveSlots = fusionVersion.aliveSlots();
        aliveProviders = new IndexProvider[aliveSlots.length];
        providers = new EnumMap<>( IndexSlot.class );
        fill( providers, IndexProvider.EMPTY );
        for ( int i = 0; i < aliveSlots.length; i++ )
        {
            switch ( aliveSlots[i] )
            {
            case GENERIC:
                IndexProvider generic = mockProvider( GenericNativeIndexProvider.class, "generic" );
                providers.put( GENERIC, generic );
                aliveProviders[i] = generic;
                break;
            case LUCENE:
                IndexProvider lucene = mockProvider( IndexProvider.class, "lucene" );
                providers.put( LUCENE, lucene );
                aliveProviders[i] = lucene;
                break;
            default:
                throw new RuntimeException();
            }
        }
        fusionIndexProvider = new FusionIndexProvider(
                providers.get( GENERIC ),
                providers.get( LUCENE ),
                fusionVersion.slotSelector(), DESCRIPTOR, NONE, mock( FileSystemAbstraction.class ), false );
        instanceSelector = new InstanceSelector<>( providers );
    }

    private static IndexProvider mockProvider( Class<? extends IndexProvider> providerClass, String name )
    {
        IndexProvider mock = mock( providerClass );
        when( mock.getProviderDescriptor() ).thenReturn( new IndexProviderDescriptor( name, "1" ) );
        return mock;
    }

    @Test
    public void mustSelectCorrectTargetForAllGivenValueCombinations()
    {
        // given
        EnumMap<IndexSlot,Value[]> values = FusionIndexTestHelp.valuesByGroup();
        Value[] allValues = FusionIndexTestHelp.allValues();

        for ( IndexSlot slot : IndexSlot.values() )
        {
            Value[] group = values.get( slot );
            for ( Value value : group )
            {
                // when
                IndexProvider selected = instanceSelector.select( slotSelector.selectSlot( array( value ), CATEGORY_OF ) );

                // then
                assertSame( orLucene( providers.get( slot ) ), selected );
            }
        }

        // All composite values should go to generic
        for ( Value firstValue : allValues )
        {
            for ( Value secondValue : allValues )
            {
                // when
                IndexProvider selected = instanceSelector.select( slotSelector.selectSlot( array( firstValue, secondValue ), CATEGORY_OF ) );

                // then
                assertSame( providers.get( GENERIC ), selected );
            }
        }
    }

    @Test
    public void mustCombineSamples()
    {
        // given
        int sumIndexSize = 0;
        int sumUniqueValues = 0;
        int sumSampleSize = 0;
        IndexSample[] samples = new IndexSample[providers.size()];
        for ( int i = 0; i < samples.length; i++ )
        {
            int indexSize = random.nextInt( 0, 1_000_000 );
            int uniqueValues = random.nextInt( 0, 1_000_000 );
            int sampleSize = random.nextInt( 0, 1_000_000 );
            samples[i] = new IndexSample( indexSize, uniqueValues, sampleSize );
            sumIndexSize += indexSize;
            sumUniqueValues += uniqueValues;
            sumSampleSize += sampleSize;
        }

        // when
        IndexSample fusionSample = FusionIndexSampler.combineSamples( Arrays.asList( samples ) );

        // then
        assertEquals( sumIndexSize, fusionSample.indexSize() );
        assertEquals( sumUniqueValues, fusionSample.uniqueValues() );
        assertEquals( sumSampleSize, fusionSample.sampleSize() );
    }

    @Test
    public void getPopulationFailureMustThrowIfNoFailure()
    {
        // when
        // ... no failure
        IllegalStateException failure = new IllegalStateException( "not failed" );
        for ( IndexProvider provider : aliveProviders )
        {
            when( provider.getPopulationFailure( any( StoreIndexDescriptor.class ) ) ).thenThrow( failure );
        }

        // then
        try
        {
            fusionIndexProvider.getPopulationFailure( AN_INDEX );
            fail( "Should have failed" );
        }
        catch ( IllegalStateException e )
        {   // good
        }
    }

    @Test
    public void getPopulationFailureMustReportFailureWhenAnyFailed()
    {
        for ( IndexProvider failingProvider : aliveProviders )
        {
            // when
            String failure = "failure";
            IllegalStateException exception = new IllegalStateException( "not failed" );
            for ( IndexProvider provider : aliveProviders )
            {
                if ( provider == failingProvider )
                {
                    when( provider.getPopulationFailure( any( StoreIndexDescriptor.class ) ) ).thenReturn( failure );
                }
                else
                {
                    when( provider.getPopulationFailure( any( StoreIndexDescriptor.class ) ) ).thenThrow( exception );
                }
            }

            // then
            assertThat( fusionIndexProvider.getPopulationFailure( AN_INDEX ), containsString( failure ) );
        }
    }

    @Test
    public void getPopulationFailureMustReportFailureWhenMultipleFail()
    {
        // when
        List<String> failureMessages = new ArrayList<>();
        for ( IndexProvider aliveProvider : aliveProviders )
        {
            String failureMessage = "FAILURE[" + aliveProvider + "]";
            failureMessages.add( failureMessage );
            when( aliveProvider.getPopulationFailure( any( StoreIndexDescriptor.class ) ) ).thenReturn( failureMessage );
        }

        // then
        String populationFailure = fusionIndexProvider.getPopulationFailure( AN_INDEX );
        for ( String failureMessage : failureMessages )
        {
            assertThat( populationFailure, containsString( failureMessage ) );
        }
    }

    @Test
    public void shouldReportFailedIfAnyIsFailed()
    {
        // given
        IndexProvider provider = fusionIndexProvider;

        for ( InternalIndexState state : InternalIndexState.values() )
        {
            for ( IndexProvider failedProvider : aliveProviders )
            {
                // when
                for ( IndexProvider aliveProvider : aliveProviders )
                {
                    setInitialState( aliveProvider, failedProvider == aliveProvider ? InternalIndexState.FAILED : state );
                }
                InternalIndexState initialState = provider.getInitialState( AN_INDEX );

                // then
                assertEquals( InternalIndexState.FAILED, initialState );
            }
        }
    }

    @Test
    public void shouldReportPopulatingIfAnyIsPopulating()
    {
        // given
        for ( InternalIndexState state : array( InternalIndexState.ONLINE, InternalIndexState.POPULATING ) )
        {
            for ( IndexProvider populatingProvider : aliveProviders )
            {
                // when
                for ( IndexProvider aliveProvider : aliveProviders )
                {
                    setInitialState( aliveProvider, populatingProvider == aliveProvider ? InternalIndexState.POPULATING : state );
                }
                InternalIndexState initialState = fusionIndexProvider.getInitialState( AN_INDEX );

                // then
                assertEquals( InternalIndexState.POPULATING, initialState );
            }
        }
    }

    @Test
    public void shouldBlessWithAllProviders() throws MisconfiguredIndexException
    {
        // given
        IndexDescriptor indexDescriptor = AN_INDEX;

        // when
        for ( IndexProvider aliveProvider : aliveProviders )
        {
            when( aliveProvider.bless( any( IndexDescriptor.class ) ) ).then( returnsFirstArg() );
        }
        indexDescriptor = fusionIndexProvider.bless( indexDescriptor );

        // then
        for ( IndexProvider aliveProvider : aliveProviders )
        {
            verify( aliveProvider, times( 1 ) ).bless( any( IndexDescriptor.class ) );
        }
    }

    private void setInitialState( IndexProvider mockedProvider, InternalIndexState state )
    {
        when( mockedProvider.getInitialState( any( StoreIndexDescriptor.class ) ) ).thenReturn( state );
    }

    private IndexProvider orLucene( IndexProvider provider )
    {
        return provider != IndexProvider.EMPTY ? provider : providers.get( LUCENE );
    }
}
