<xsl:stylesheet version="2.0"
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
	xmlns:xs="http://www.w3.org/2001/XMLSchema">
	
<xsl:output method="html" indent="yes" version="4.0"/>

<xsl:template match="/">
	<xsl:text disable-output-escaping="yes">&lt;!DOCTYPE html&gt;
</xsl:text>
	<xsl:copy-of select="html"/>
</xsl:template>

</xsl:stylesheet>