<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema">
	
<xsl:output method="text" indent="no"/>

<xsl:template match="/">
<![CDATA[
PREFIX elmo: <http://bp4mc2.org/elmo/def#>
CONSTRUCT {
	?rep ?repp ?repo.
	?fragment ?fragmentp ?fragmento.
	?repchild ?repchildp ?repchildo.
	?fragmentchild ?fragmentchildp ?fragmentchildo.
	?rep elmo:query ?query.
	?repchild elmo:query ?querychild.
	?formfragment ?formfragmentp ?formfragmento.
	?form ?formp ?formo.
}
WHERE {
	GRAPH <]]><xsl:value-of select="input/stage"/><![CDATA[>{
		{
			?rep ?repp ?repo.
			OPTIONAL {?rep elmo:query/elmo:query ?query}
			FILTER (?rep=<]]><xsl:value-of select="input/representation"/><![CDATA[>)
		}
		UNION
		{<]]><xsl:value-of select="input/representation"/><![CDATA[> elmo:fragment ?fragment. ?fragment ?fragmentp ?fragmento }
		UNION
		{
			<]]><xsl:value-of select="input/representation"/><![CDATA[> elmo:contains ?repchild.
			?repchild ?repchildp ?repchildo.
			OPTIONAL { ?repchild elmo:fragment ?fragmentchild. ?fragmentchild ?fragmentchildp ?fragmentchildo }
			OPTIONAL { ?repchild elmo:query/elmo:query ?querychild }
		}
		UNION
		{
			<]]><xsl:value-of select="input/representation"/><![CDATA[> elmo:queryForm ?form.
			?form ?formp ?formo.
			OPTIONAL {?form elmo:fragment ?formfragment. ?formfragment ?formfragmentp ?formfragmento}
		}
	}
}
]]>
</xsl:template>

</xsl:stylesheet>