package no.slomic.smarthytte.schema

import graphql.GraphQLContext
import graphql.execution.CoercedVariables
import graphql.language.Value
import graphql.schema.Coercing
import graphql.schema.CoercingParseLiteralException
import graphql.schema.CoercingParseValueException
import graphql.schema.CoercingSerializeException
import graphql.schema.GraphQLScalarType
import kotlinx.datetime.Instant
import java.util.*

object InstantScalar {
    val graphqlScalar: GraphQLScalarType = GraphQLScalarType.newScalar()
        .name("Instant")
        .description("An ISO-8601 compliant DateTime scalar")
        .coercing(InstantCoercing)
        .build()
}

object InstantCoercing : Coercing<Instant, String> {
    override fun serialize(dataFetcherResult: Any, graphQLContext: GraphQLContext, locale: Locale): String =
        when (dataFetcherResult) {
            is Instant -> dataFetcherResult.toString() // ISO-8601
            else -> throw CoercingSerializeException("Not an Instant")
        }

    override fun parseValue(input: Any, graphQLContext: GraphQLContext, locale: Locale): Instant = when (input) {
        is String -> Instant.parse(input)
        else -> throw CoercingParseValueException("Not a valid ISO-8601 string")
    }

    override fun parseLiteral(
        input: Value<*>,
        variables: CoercedVariables,
        graphQLContext: GraphQLContext,
        locale: Locale,
    ): Instant = when (input) {
        is graphql.language.StringValue -> Instant.parse(input.value)
        else -> throw CoercingParseLiteralException("Expected ISO-8601 string literal")
    }
}
