/**
 * 
 */
package com.transcript.main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.transcript.constants.CognitiveConstants;
import com.transcript.model.CognitiveResponse;
import com.transcript.model.QueryString;
import com.transcript.model.VideoUploadResponse;
import com.transcript.util.TranscriptGenerationUtil;


/**
 * @author arunangsu.sahu
 *
 */
public class TranscriptGenerator {
	
	private static Logger logger = LogManager.getLogger(TranscriptGenerator.class);

	/**
	 * 
	 */
	public TranscriptGenerator() {
		// No-Argument Constructor
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// Main Function

	}

	public void getTranscriptDetails() {
		// get Profile Parameters from Microsoft Cognitive account

		// location
		String location = "westus2";
		//accountId
		String id = "123-c45";
		// apiKey
		String apiKey = CognitiveConstants.BLANK;

		// get Account Access Token
		String accountAccessTokenRetrieval = getAccountAccessToken(location, id, apiKey);
		if (StringUtils.isNotBlank(accountAccessTokenRetrieval)) {
			
			// get videoId
			String videoId = getVideoIdAfterVideoUpload(location, id, accountAccessTokenRetrieval);
			if (StringUtils.isNotBlank(videoId)) {
				
				// get videoAccessToken
				String videoAccessTokenRetrieval = getVideoAccessToken(location, id, videoId, apiKey);
				
				if(StringUtils.isNotBlank(videoAccessTokenRetrieval)) {
					
					// get the Transcript
					String transcriptFull = getTranscriptFromVideo(location, id, videoAccessTokenRetrieval, videoId);

					logger.debug(transcriptFull.trim());
				}
			}
		}
	}

	/**
	 * get the video Transcripts generated by 
	 * Microsoft Cognitive Video Indexer V2API
	 * @param location
	 * @param accountId
	 * @param videoAccessTokenRetrieval
	 * @param videoId
	 * @return
	 */
	public static String getTranscriptFromVideo(String location, String accountId, String videoAccessTokenRetrieval,
			String videoId) {

		String apiURL = CognitiveConstants.API_URL;
		String accounts = CognitiveConstants.ACCOUNTS;
		String videos = CognitiveConstants.VIDEOS;
		String index = CognitiveConstants.INDEX;
		String amp = CognitiveConstants.AMP;
		String transcriptFinal;
		CognitiveResponse cognitiveResponse = new CognitiveResponse();

		//Building Query for Video Transcript retrieval
		StringBuilder apiURLFinal = new StringBuilder();
		apiURLFinal.append(apiURL).append(location).append(accounts).append(accountId).append(videos).append(videoId)
				.append(index).append(videoAccessTokenRetrieval).append(amp);

		QueryString qs = new QueryString("language", "English");
		apiURLFinal.append(qs);
		String url = apiURLFinal.toString();
		logger.debug(url);

		// wait for the video index to finish
		while (true) {
			try {
				Thread.sleep(20000);
				String cognitiveResponseJson = getResponse(apiURLFinal.toString(), false, CognitiveConstants.BLANK, CognitiveConstants.HTTP_GET);
				cognitiveResponse = getCognitiveResponse(cognitiveResponseJson);
				//Once Video Indexing is finished
				if (!cognitiveResponse.getState().equals("Uploaded")
						&& !cognitiveResponse.getState().equals("Processing")) {
					break;
				}
			} catch (InterruptedException ie) {
				logger.error(ie);
				Thread.currentThread().interrupt();
			}
		}
		//retrieve the Transcript
		transcriptFinal = TranscriptGenerationUtil.getTranscriptsDetailsFromVideos(cognitiveResponse);
		if (null == transcriptFinal)
			transcriptFinal = CognitiveConstants.BLANK;

		return transcriptFinal.trim();
	}

	/**
	 * get the video indexing object(CognitiveResponse) 
	 * from the video indexing Response JSON
	 * @param cognitiveResponseJson
	 * @return CognitiveResponse
	 */
	public static CognitiveResponse getCognitiveResponse(String cognitiveResponseJson) {
		CognitiveResponse cognitiveResponse= new CognitiveResponse();
		try {
			cognitiveResponse = getObjectMapper().readValue(cognitiveResponseJson, CognitiveResponse.class);
		
		} catch (JsonParseException jpe) {
			logger.error(jpe);				
		} catch (JsonMappingException jme) {
			logger.error(jme);
		} catch (IOException ioe) {
			logger.error(ioe);
		}
		
		return cognitiveResponse;
		
	}
	/** get the Video Access Authorization token for 
	 * retrieving Video Indexed Cognitive Object 
	 * @param location
	 * @param accountId
	 * @param videoId
	 * @return
	 */
	public static String getVideoAccessToken(String location, String accountId, String videoId, String apiKey) {

		String apiURL = CognitiveConstants.API_URL;
		String auth = CognitiveConstants.AUTH;
		String accounts = CognitiveConstants.ACCOUNTS;
		String videos = CognitiveConstants.VIDEOS;
		String accessToken = CognitiveConstants.ACCESS_TOKEN;
		String videoAccessTokenFinal = CognitiveConstants.BLANK;

		//Building the url for Video Access Token retrieval
		StringBuilder accessTokenUrl = new StringBuilder();
		accessTokenUrl.append(apiURL).append(auth).append(location).append(accounts).append(accountId).append(videos)
				.append(videoId).append(accessToken);

		logger.info(accessTokenUrl.toString());
		
		//retrieve the video access token
		String videoAccessTokenResponse = getResponse(accessTokenUrl.toString(), true, apiKey, CognitiveConstants.HTTP_GET);

		try {
			videoAccessTokenFinal = getObjectMapper().readValue(videoAccessTokenResponse, String.class);
		} catch (JsonParseException jpe) {
			logger.error(jpe);
		} catch (JsonMappingException jme) {
			logger.error(jme);
		} catch (IOException ioe) {
			logger.error(ioe);
		}

		return videoAccessTokenFinal.trim();
	}

	/**
	 * get the generated Video Id after a video is uploaded
	 * @param location
	 * @param accountId
	 * @param accessToken
	 * @return
	 */
	public static String getVideoIdAfterVideoUpload(String location, String accountId, String accessToken) {

		String apiURL = CognitiveConstants.API_URL;
		String accounts = CognitiveConstants.ACCOUNTS;
		String videos = CognitiveConstants.VIDEO_WITH_ACCESS_TOKEN;
		String amp = CognitiveConstants.AMP;
		VideoUploadResponse videoUploadResponse = new VideoUploadResponse();
		
		//Build Video upload and Video Id retrieval Url
		StringBuilder apiURLFinal = new StringBuilder();
		apiURLFinal.append(apiURL).append(location).append(accounts).append(accountId).append(videos)
				.append(accessToken).append(amp);
		
		//Sample Video Upload Details
		QueryString qs = new QueryString("name", "my-video");
		qs.add("description", "my-video-description");
		qs.add("language", "English");
		qs.add("videoUrl", "http://url-to-the-video");// need the url
		qs.add("indexingPreset", "Default");
		qs.add("streamingPreset", "Default");
		qs.add("privacy", "Private");

		apiURLFinal.append(qs);
		String url = apiURLFinal.toString();
		logger.debug(url);
		
		//Get The Video Upload Response
		String videoUploadResponseJson = getResponse(apiURLFinal.toString(), false, CognitiveConstants.BLANK,CognitiveConstants.HTTP_POST );

		try {
			videoUploadResponse = getObjectMapper().readValue(videoUploadResponseJson, VideoUploadResponse.class);
		} catch (JsonParseException jse) {
			logger.error(jse);
		} catch (JsonMappingException jme) {
			logger.error(jme);
		} catch (IOException ioe) {
			logger.error(ioe);
		}

		return videoUploadResponse.getId().trim();
	}

	/**
	 * get the Microsoft Cognitive account access token
	 * @param location
	 * @param accountId
	 * @param apiKey
	 * @return
	 */
	public static String getAccountAccessToken(String location, String accountId, String apiKey) {

		String apiURL = CognitiveConstants.API_URL;
		String auth = CognitiveConstants.AUTH;
		String accounts = CognitiveConstants.ACCOUNTS;
		String accessToken = CognitiveConstants.ACCESS_TOKEN_WITH_ALLOW_EDIT;
		String accessTokenFinal = CognitiveConstants.BLANK;
		StringBuilder accessTokenUrl = new StringBuilder();
		
		//Build the account access token retrieval url
		accessTokenUrl.append(apiURL)
		.append(auth)
		.append(location)
		.append(accounts)
		.append(accountId)
		.append(accessToken);
		
		//retrieve the account access token
		String accessTokenResponse = getResponse(accessTokenUrl.toString(), true, apiKey, CognitiveConstants.HTTP_GET);

		try {
			accessTokenFinal = getObjectMapper().readValue(accessTokenResponse, String.class);
		} catch (JsonParseException jse) {
			logger.error(jse);
		} catch (JsonMappingException jme) {
			logger.error(jme);
		} catch (IOException ioe) {
			logger.error(ioe);
		}

		return accessTokenFinal.trim();

	}

	/**
	 * @return
	 */
	public static ObjectMapper getObjectMapper() {

		ObjectMapper mapper = new ObjectMapper();
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		return mapper;
	}

	/**
	 * @param targetURL
	 * @param apiKeyAddition
	 * @param apiKey
	 * @return
	 */
	public static String getResponse(String targetURL, boolean apiKeyAddition, String apiKey, String httpMethod) {
		String responseEntity = CognitiveConstants.BLANK;
		try {
			URL restServiceURL = new URL(targetURL);
			HttpURLConnection httpConnection = (HttpURLConnection) restServiceURL.openConnection();
			httpConnection.setRequestMethod(httpMethod);
			httpConnection.setRequestProperty("Accept", "application/json");

			// Need to validate
			if (apiKeyAddition)
				httpConnection.setRequestProperty("Ocp-Apim-Subscription-Key", apiKey);

			if (httpConnection.getResponseCode() != 200) {
				logger.error(httpConnection.getResponseCode());
			}
			BufferedReader responseBuffer = new BufferedReader(
					new InputStreamReader((httpConnection.getInputStream())));
			String output;
			logger.debug("Output from Server:");
			StringBuilder responseFromURL = new StringBuilder();
			while ((output = responseBuffer.readLine()) != null) {
				logger.debug(output);
				responseFromURL.append(output);
			}
			httpConnection.disconnect();
			responseEntity = responseFromURL.toString();
		} catch (MalformedURLException mue) {
			logger.error(mue);
		} catch (IOException ioe) {
			logger.error(ioe);
		}
			return responseEntity;
	}

}
