package com.coxautodev.graphql.tools

import graphql.ExecutionInput
import graphql.GraphQL
import graphql.language.StringValue
import graphql.schema.Coercing
import graphql.schema.GraphQLScalarType
import org.junit.Assert
import org.junit.Test

class MethodFieldResolverTest {

    @Test
    fun `should handle scalar types as method input argument`() {
        val schema = SchemaParser.newParser()
                .schemaString("""
                    scalar CustomScalar
                    type Query {
                        test(input: CustomScalar): Int
                    }
                    """.trimIndent()
                )
                .scalars(customScalarType)
                .resolvers(object : GraphQLQueryResolver {
                    fun test(scalar: CustomScalar) = scalar.value.length
                })
                .build()
                .makeExecutableSchema()

        val gql = GraphQL.newGraphQL(schema).build()

        val result = gql
                .execute(ExecutionInput.newExecutionInput()
                        .query("""
                            query Test(${"$"}input: CustomScalar) {
                                test(input: ${"$"}input)
                            }
                            """.trimIndent())
                        .variables(mapOf("input" to "FooBar"))
                        .context(Object())
                        .root(Object()))

        Assert.assertEquals(6, result.getData<Map<String, Any>>()["test"])
    }

    @Test
    fun `should handle lists of scalar types`() {
        val schema = SchemaParser.newParser()
                .schemaString("""
                    scalar CustomScalar
                    type Query {
                        test(input: [CustomScalar]): Int
                    }
                    """.trimIndent()
                )
                .scalars(customScalarType)
                .resolvers(object : GraphQLQueryResolver {
                    fun test(scalars: List<CustomScalar>) = scalars.map { it.value.length }.sum()
                })
                .build()
                .makeExecutableSchema()

        val gql = GraphQL.newGraphQL(schema).build()

        val result = gql
                .execute(ExecutionInput.newExecutionInput()
                        .query("""
                            query Test(${"$"}input: [CustomScalar]) {
                                test(input: ${"$"}input)
                            }
                            """.trimIndent())
                        .variables(mapOf("input" to listOf("Foo", "Bar")))
                        .context(Object())
                        .root(Object()))

        Assert.assertEquals(6, result.getData<Map<String, Any>>()["test"])
    }

    /**
     * Custom Scalar Class type that doesn't work with Jackson serialization/deserialization
     */
    class CustomScalar private constructor(private val internalValue: String) {
        val value get() = internalValue

        companion object {
            fun of(input: Any?) = when (input) {
                is String -> CustomScalar(input)
                else -> null
            }
        }
    }

    private val customScalarType: GraphQLScalarType = GraphQLScalarType.newScalar()
            .name("CustomScalar")
            .description("customScalar")
            .coercing(object : Coercing<CustomScalar, String> {

                override fun parseValue(input: Any?) = CustomScalar.of(input)

                override fun parseLiteral(input: Any?) = when (input) {
                    is StringValue -> CustomScalar.of(input.value)
                    else -> null
                }

                override fun serialize(dataFetcherResult: Any?) = when (dataFetcherResult) {
                    is CustomScalar -> dataFetcherResult.value
                    else -> null
                }
            })
            .build()

}
