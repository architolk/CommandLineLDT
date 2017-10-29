package nl.architolk.ldtcmd;

import java.io.InputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.List;
import java.util.ArrayList;

public class Sparql {

	public static InputStream executeRequest(String endPoint, String query) throws UnsupportedEncodingException, IOException {
		
		//Create the client
		CloseableHttpClient httpclient = HttpClients.createDefault();
		
		//Create the request
		HttpPost httpRequest = new HttpPost(endPoint);
		
		//Add post name/value pairs: one name/value containing the query parameter
		List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
		nameValuePairs.add(new BasicNameValuePair("query", query));
		httpRequest.setEntity(new UrlEncodedFormEntity(nameValuePairs));
		httpRequest.addHeader("accept","application/sparql-results+xml,application/rdf+xml");
		httpRequest.addHeader("accept-encoding","UTF-8");
		
		//Execute the request
		CloseableHttpResponse response = httpclient.execute(httpRequest);

		int status = response.getStatusLine().getStatusCode();
		
		if (status < 200 || status >= 300) throw new IOException(response.getStatusLine().toString());
		HttpEntity entity = response.getEntity();
		if (entity==null) throw new IOException("No content http error");

		return entity.getContent();
	}

}
