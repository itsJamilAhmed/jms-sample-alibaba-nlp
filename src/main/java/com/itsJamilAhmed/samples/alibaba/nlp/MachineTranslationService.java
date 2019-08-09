package com.itsJamilAhmed.samples.alibaba.nlp;

import com.aliyuncs.DefaultAcsClient;  
import com.aliyuncs.IAcsClient;  
import com.aliyuncs.alimt.model.v20181012.TranslateGeneralRequest;
import com.aliyuncs.alimt.model.v20181012.TranslateGeneralResponse;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.exceptions.ServerException;
import com.aliyuncs.http.MethodType;  
import com.aliyuncs.profile.DefaultProfile;
import com.alibaba.fastjson.JSONObject; 

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

class MachineTranslationService {
	
	final int REQUEST_OK_CODE = 200;
	private DefaultProfile profile;
	private IAcsClient client;
	private TranslateGeneralRequest generalRequestToEnglish;
	private TranslateGeneralRequest generalRequestToChinese;
	private TranslateGeneralResponse generalResponse;
	private boolean isSimulation = false;
	
	final String SIMULATED_ENGLISH = "Test Message";
	final String SIMULATED_CHINESE = "测试消息";
	
	public MachineTranslationService(String serviceRegion, String accessKeyId, String accessKeySecret) throws Exception {
				
		profile = DefaultProfile.getProfile(serviceRegion,accessKeyId,accessKeySecret); 
		client = new DefaultAcsClient(profile);

		
		// Keep a request object ready to go for each translation direction
		generalRequestToEnglish = new TranslateGeneralRequest();
		generalRequestToEnglish.setSourceLanguage("zh");	// From Chinese
		generalRequestToEnglish.setTargetLanguage("en");	// To English
		generalRequestToEnglish.setMethod(MethodType.POST);  
		generalRequestToEnglish.setFormatType("text"); 

		generalRequestToChinese = new TranslateGeneralRequest();
		generalRequestToChinese.setSourceLanguage("en");	// From English
		generalRequestToChinese.setTargetLanguage("zh");	// To Chinese
		generalRequestToChinese.setMethod(MethodType.POST);  
		generalRequestToChinese.setFormatType("text"); 
		
		selfTest();	// Will trigger an exception on creation if the parameters were invalid
 
	}
	
	public MachineTranslationService(boolean simulationMode) throws Exception {
		
		// Will only accept this to be true, handle the false a better way?
		if (simulationMode)
		{
			this.isSimulation = true;
		}
		else
		{
			throw new Exception ("This constructor can only be called with value true.");
		}
		
	}
	
	private void selfTest () throws Exception {
		// Need to try out a request to make sure the parameters are good.
		try {
			translateEnglishToChinese("hello");
		} catch (Exception e) {
			throw new Exception(e.getMessage());
		}
		
		// Findings: When there is an invalid access key or secret, the SDK is not throwing the proper exception for it and just prints "Invoke_Error,..." to stdout!! Grr
		// So when the keys are incorrect, you get a misleading ClientException mentioning the failure to connect to the endpoint.
		// "SDK.InvalidRegionId : Can not find endpoint to access."		
		
	}
	
	public String translateChineseToEnglish (String translationText) throws Exception {

		if (this.isSimulation) {
			return SIMULATED_ENGLISH;
		}
		
		String translationResponse = "";
		try {
			generalRequestToEnglish.setSourceText(URLEncoder.encode(translationText,"UTF-8"));
			generalResponse = client.getAcsResponse(generalRequestToEnglish);  
			JSONObject translationResponseJSON = (JSONObject) JSONObject.toJSON(generalResponse);
			
			if (translationResponseJSON.getInteger("code") == REQUEST_OK_CODE) {
				// Request was OK
				translationResponse = translationResponseJSON.getJSONObject("data").getString("translated");
			}
			else {
				// Not seen one of these yet so not sure what it will contain...
				throw new Exception("Received a non-OK response from SDK: " + translationResponseJSON.toString());
			}
			
		} catch (UnsupportedEncodingException e) {
			// Do nothing
		} catch (ServerException se) {
			throw new Exception("ServerException from Alibaba SDK: " + se.getMessage());
		} catch (ClientException ce) {
			// Pass it up to caller
			throw new Exception("ClientException from Alibaba SDK: " + ce.getMessage());
		}
		
		return translationResponse;
	}
	
	public String translateEnglishToChinese (String translationText) throws Exception {
		
		if (this.isSimulation) {
			return SIMULATED_CHINESE;
		}
		String translationResponse = "";
		try {
			generalRequestToChinese.setSourceText(URLEncoder.encode(translationText,"UTF-8"));
			generalResponse = client.getAcsResponse(generalRequestToChinese);  
			JSONObject translationResponseJSON = (JSONObject) JSONObject.toJSON(generalResponse);
			
			if (translationResponseJSON.getInteger("code") == REQUEST_OK_CODE) {
				// Request was OK
				translationResponse = translationResponseJSON.getJSONObject("data").getString("translated");
			}
			else {
				// Not seen one of these yet so not sure what it will contain...
				throw new Exception("Received a non-OK response from SDK: " + translationResponseJSON.toString());
			}
			
			
		} catch (UnsupportedEncodingException e) {
			// Do nothing
		} catch (ServerException se) {
			throw new Exception("ServerException from Alibaba SDK: " + se.getMessage());
		} catch (ClientException ce) {
			// Pass it up to caller
			throw new Exception("ClientException from Alibaba SDK: " + ce.getMessage());
		}
		
		return translationResponse;
	}
		
}
