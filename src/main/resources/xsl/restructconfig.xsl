<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema"
	xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
	xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
	xmlns:elmo="http://bp4mc2.org/elmo/def#"
>

<xsl:output method="xml" indent="yes"/>

<xsl:template match="/">
	<rdf:RDF>
		<xsl:for-each-group select="rdf:RDF/rdf:Description[exists(elmo:data[1]) or exists(elmo:query[.!='']) or exists(elmo:service[1]) or exists(elmo:webpage[1]) or exists(elmo:queryForm[1]) or rdf:type/@rdf:resource='http://bp4mc2.org/elmo/def#Production']" group-by="@rdf:about"><xsl:sort select="concat(elmo:index[1],'~')"/>
			<rdf:Description rdf:about="{@rdf:about}">
				<xsl:copy-of select="current-group()/*"/>
				<!-- Not very nice: it would be better to include the original data! -->
				<xsl:if test="exists(elmo:data[1])">
					<elmo:query>
						<![CDATA[
						PREFIX elmo: <http://bp4mc2.org/elmo/def#>
						CONSTRUCT {
							?s?p?o.
							?sc?pc?oc.
							?scc?pcc?occ.
						}
						WHERE { GRAPH <@STAGE@>
						{<]]><xsl:value-of select="@rdf:about"/><![CDATA[> elmo:data ?s.
							?s?p?o.
							OPTIONAL {
								?s elmo:data ?sc.
								?sc ?pc ?oc.
								OPTIONAL {
									?sc elmo:data ?scc.
									?scc ?pcc ?occ.
								}
							}
						}}
						]]>
					</elmo:query>
				</xsl:if>
			</rdf:Description>
		</xsl:for-each-group>
	</rdf:RDF>
</xsl:template>

</xsl:stylesheet>