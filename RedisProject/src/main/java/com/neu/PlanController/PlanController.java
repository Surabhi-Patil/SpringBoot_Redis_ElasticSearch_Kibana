package com.neu.PlanController;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.neu.ValidationUtils.*;

import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonSimpleJsonParser;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.neu.Decrypt.AES;
import com.neu.RedisConfig.RedisConfiguration;


import redis.clients.jedis.Jedis;

@RestController
public class PlanController {
	
	final String secretKey = "AbditSecretKey";

	@Autowired
	Jedis jedis;
	
	@RequestMapping(method=RequestMethod.POST, value="/generateToken")
	public String generateToken(HttpServletRequest request, HttpServletResponse response, @RequestBody String jsondata) {
			 	
	 	DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
	    Calendar cal = Calendar.getInstance();
	    cal.add(Calendar.SECOND, 300);
	    
	    
	    JSONObject jo = new JSONObject(jsondata);
	    
	 	jo.put("expiry", dateFormat.format(cal.getTime()));
	    
	 	System.out.println("Current Date Time : " + dateFormat.format(cal.getTime()));
	 	
	    
	    
	    String originalString = jo.toString();
	    JSONParser jp = new JSONParser();
	    
	    String encryptedString = AES.encrypt(originalString, secretKey) ;
	     
	    System.out.println(originalString);
	    System.out.println(encryptedString);
	    
	    JSONObject jsonToken = new JSONObject();
	    jsonToken.put("token", encryptedString);
	    
	    return jsonToken.toString();
	    
	}
	
	
	@RequestMapping(method=RequestMethod.POST, value="/insertPlan")
	public String createPlanedService(HttpServletRequest request, HttpServletResponse response, @RequestBody String jsondata) throws JSONException, ProcessingException, IOException, NoSuchAlgorithmException  {
		final String secretKey = "AbditSecretKey";
		
		if(request.getHeader("Authorization") == null) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			JSONObject errMessage = new JSONObject();
        	errMessage.put("message", "Authorization failed!");
        	return errMessage.toString();
		}
		
		String header  = request.getHeader("Authorization");
		if(header.contains("Bearer") && header != null) {
			String token = header.split(" ")[1];
			String decryptedToken = AES.decrypt(token, secretKey);
			
			try{
			JSONObject js = new JSONObject(decryptedToken);
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			
			Calendar cal = Calendar.getInstance();
		    Date currentDate = cal.getTime();
		    
		    Date date1 = dateFormat.parse((String) js.get("expiry")); 
		    
		    if(currentDate.compareTo(date1) > 0) {
		    	response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				JSONObject errMessage = new JSONObject();
	        	errMessage.put("message", "Token expired!");
	        	return errMessage.toString();
		    } 
			}
			catch(Exception e) {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				JSONObject errMessage = new JSONObject();
	        	errMessage.put("message", "Authorization failed!");
	        	return errMessage.toString();
			}
			
		} else {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			JSONObject errMessage = new JSONObject();
        	errMessage.put("message", "Authorization failed!");
        	return errMessage.toString();
		}
		
		
		JSONObject jsonObject = new JSONObject(jsondata);
		
		
        
        System.out.println("@@@@@@@@@@@@@@@@@@@"+jsonObject+"@@@@@@@@@@@@@@@@@@@");
        
		
		
		File schemaFile = new File("C:\\Users\\Surabhi Patil\\Desktop\\NEU\\NEU ABDIT\\RedisProject\\schema.json");

		if (ValidationUtils.isJsonValid(schemaFile, jsondata)){
			String[] resArr = readJson(jsonObject);
        	JSONObject jsonMessage = new JSONObject();
        	jsonMessage.put("message", "The json data was validated and successfully uploaded in Redis");
        	jsonMessage.put("planId", resArr[1]);
        	response.setHeader("ETag", resArr[0]);
        	return jsonMessage.toString();

	    }else{
	    	JSONObject jsonMessage = new JSONObject();
        	jsonMessage.put("message", "Data validation failed!");
        	response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        	return jsonMessage.toString();
	    }
		
        
		
	}
	
		public String generateHash(String jsonString) throws UnsupportedEncodingException, NoSuchAlgorithmException {
		byte[] bytesOfMessage = jsonString.getBytes("UTF-8");
		MessageDigest md = MessageDigest.getInstance("MD5");
		byte[] thedigest = md.digest(bytesOfMessage);
		return thedigest.toString();
		}

	
		public String[] readJson(JSONObject jsonObject) throws UnsupportedEncodingException, NoSuchAlgorithmException {
			String jsonString = jsonObject.toString();
			String hashString = generateHash(jsonString);
			Map<String, String> plan = new HashMap<>();
			for(Object key : jsonObject.keySet()) {
						
						String planAttrKey = (String) key;
						
						Object val = jsonObject.get(planAttrKey);
						
						if(val instanceof JSONObject) {
							JSONObject planCostShareJson = (JSONObject) val;
							Map<String, String> planCostShare = new HashMap<>();
							
							for(Object pcsKey : planCostShareJson.keySet()) {
								planCostShare.put((String) pcsKey, String.valueOf(planCostShareJson.get((String) pcsKey)));
							}
							
							String planCostShareSetKey = "plan_"+(String) jsonObject.get("objectId")+"_planCostShare";
							jedis.hmset(planCostShareSetKey, planCostShare);
							
						} else if(val instanceof JSONArray) {
							JSONArray jarr = (JSONArray) val;
							for(int i=0; i < jarr.length(); i++) {
								JSONObject jarrObj = ((JSONObject) jarr.get(i));
								Map<String, String> linkedPlanServMap = new HashMap<>();
								for(Object linkPlanServArrayKey : jarrObj.keySet()) {
									
									if(jarrObj.get((String) linkPlanServArrayKey) instanceof JSONObject) {
										if(String.valueOf(linkPlanServArrayKey).equals("linkedService")) {
											System.out.println("xxxxxxxxxxxxxxxxxxxxxxxxxxx");
											String linkedServiceKey = "linkedPlanServices_"+(String) jarrObj.get("objectId")+"_linkedService";
											Map<String, String> linkedServiceMap = new HashMap<>();
											JSONObject linkedServiceJson = (JSONObject) jarrObj.get((String) linkPlanServArrayKey);
											for(Object linkedServiceJsonKey : linkedServiceJson.keySet() ) {
												if (!String.valueOf(linkedServiceJson.get((String) linkedServiceJsonKey)).contains("{")) {
													System.out.println("sssssssssssssssssssssssssssssssssssssss");
												linkedServiceMap.put((String) linkedServiceJsonKey, String.valueOf(linkedServiceJson.get((String) linkedServiceJsonKey)));
												}
											}
											jedis.hmset(linkedServiceKey, linkedServiceMap);
										}
										
										if(String.valueOf(linkPlanServArrayKey).equals("planserviceCostShares")) {
											String planserviceCostShareKey = "linkedPlanServices_"+(String) jarrObj.get("objectId")+"_planserviceCostShare";
											Map<String, String> planserviceCostShareMap = new HashMap<>();
											JSONObject planserviceCostShareJson = (JSONObject) jarrObj.get((String) linkPlanServArrayKey);
											for(Object planserviceCostShareJsonKey : planserviceCostShareJson.keySet() ) {
												
												if (!String.valueOf(planserviceCostShareJson.get((String) planserviceCostShareJsonKey)).contains("{")) {
												planserviceCostShareMap.put((String) planserviceCostShareJsonKey, String.valueOf(planserviceCostShareJson.get((String) planserviceCostShareJsonKey)));
												}
											}
											jedis.hmset(planserviceCostShareKey, planserviceCostShareMap);
										}
										
										
									}
									if (!String.valueOf(jarrObj.get((String) linkPlanServArrayKey)).contains("{")) {
										System.out.println("mmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmmm");
									linkedPlanServMap.put((String) linkPlanServArrayKey, String.valueOf(jarrObj.get((String) linkPlanServArrayKey)));
									}
								}
								System.out.println("#######################"+linkedPlanServMap+"################");
								jedis.hmset("linkedPlanServices_"+(String) jarrObj.get("objectId"), linkedPlanServMap);
								
								jedis.sadd("plan_"+(String) jsonObject.get("objectId")+"_linkedPlanServices", "linkedPlanServices_"+(String) jarrObj.get("objectId"));
							}
							
						}
						
						if (!String.valueOf(val).contains("{")) {
							System.out.println(val);
						plan.put(planAttrKey, String.valueOf(val));
						}
						
						
					}

			String[] resArr = {hashString, (String) jsonObject.get("objectId")};
			plan.put("etag", hashString);
			jedis.hmset("plan_"+(String) jsonObject.get("objectId"), plan);
			return resArr;
		}
			
		
	
	@RequestMapping(method=RequestMethod.GET, value="/retrievePlan/{id}")
	public String getPlans(HttpServletRequest request, HttpServletResponse response ,@PathVariable(value = "id") String planId) throws JSONException {
		
		
		final String secretKey = "AbditSecretKey";
		
		if(request.getHeader("Authorization") == null) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			JSONObject errMessage = new JSONObject();
        	errMessage.put("message", "Authorization failed!");
        	return errMessage.toString();
		}
		
				
		String header  = request.getHeader("Authorization");
		if(header.contains("Bearer") && header != null) {
			String token = header.split(" ")[1];
			String decryptedToken = AES.decrypt(token, secretKey);
			
			try{
			JSONObject js = new JSONObject(decryptedToken);
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			
			Calendar cal = Calendar.getInstance();
		    Date currentDate = cal.getTime();
		    
		    Date date1 = dateFormat.parse((String) js.get("expiry")); 
		    
		    if(currentDate.compareTo(date1) > 0) {
		    	response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				JSONObject errMessage = new JSONObject();
	        	errMessage.put("message", "Token expired!");
	        	return errMessage.toString();
		    } 
			}
			catch(Exception e) {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				JSONObject errMessage = new JSONObject();
	        	errMessage.put("message", "Authorization failed!");
	        	return errMessage.toString();
			}
			
		} else {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			JSONObject errMessage = new JSONObject();
        	errMessage.put("message", "Authorization failed!");
        	return errMessage.toString();
		}
					
		
		Boolean foundFlag = false;
		
		Set<String> keySet = jedis.keys("*");
		
		if(!keySet.contains(planId)) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			JSONObject jsonMessage = new JSONObject();
			jsonMessage.put("message", "Unable to find Plan ID");
			return jsonMessage.toString();
		}
		
		Map<String,String> p = jedis.hgetAll(planId);
		
		
		if(request.getHeader("If-None-Match") != null) {

        	String headerEtag = request.getHeader("If-None-Match");
        	if(headerEtag.equals(p.get("etag"))) {
        		System.out.println("Have a plan");
    			response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
    			JSONObject jsonMessage = new JSONObject();
    			jsonMessage.put("message", "You already have the latest Plan");
    			return jsonMessage.toString();	
    		}
		}
		
		
		
		if(keySet.contains(planId)) {
			foundFlag = true;
			Set<String> linkSet = jedis.smembers(planId+"_linkedPlanServices");
			//Iterator itr = linkSet.iterator();
			ArrayList<String> linkPlanServiceArrList = new ArrayList<>();

		
		for(String lps : linkSet) {
			linkPlanServiceArrList.add(lps);
		}
		
		JSONArray linkPlanServArr = new JSONArray();
		Map<String,String> planMap = jedis.hgetAll(planId);
		JSONObject planJson = new JSONObject(planMap);
		Map<String, String> planCostShareMap = jedis.hgetAll(planId+"_planCostShare");
		JSONObject planCostShareJson = new JSONObject(planCostShareMap);

		for(String s : linkPlanServiceArrList) 
		{
			String linkPlanServId = s.split("_")[1];
			Map<String, String> linkedPlanServiceMap = jedis.hgetAll("linkedPlanServices_"+linkPlanServId);
			Map<String, String> linkedServiceMap = jedis.hgetAll("linkedPlanServices_"+linkPlanServId+"_linkedService");
			Map<String,String> planserviceCostShareMap = jedis.hgetAll("linkedPlanServices_"+linkPlanServId+"_planserviceCostShare");

			JSONObject linkedPlanServiceJson = new JSONObject(linkedPlanServiceMap);
			JSONObject linkedServiceJson = new JSONObject(linkedServiceMap);
			JSONObject planserviceCostShareMapJson = new JSONObject(planserviceCostShareMap);
			linkedPlanServiceJson.put("linkedService", linkedServiceJson);
			linkedPlanServiceJson.put("planserviceCostShareMap", planserviceCostShareMapJson);
			linkPlanServArr.put(linkedPlanServiceJson);
			planJson.put("linkedPlanServices", linkPlanServArr);
			planJson.put("plancostShares", planCostShareJson);

		}
		
		/*String linkPlanServId1 = linkPlanServiceArrList.get(0).split("_")[1];
		String linkPlanServId2 = linkPlanServiceArrList.get(1).split("_")[1];
		
		
		Map<String,String> planMap = jedis.hgetAll(planId);
		
		
		Map<String, String> planCostShareMap = jedis.hgetAll(planId+"_planCostShare");
		Map<String,String> linkedPlanServiceMap1 = jedis.hgetAll("linkedPlanServices_"+linkPlanServId1);
		Map<String,String> linkedPlanServiceMap2 = jedis.hgetAll("linkedPlanServices_"+linkPlanServId2);
		Map<String,String> linkedServiceMap1 = jedis.hgetAll("linkedPlanServices_"+linkPlanServId1+"_linkedService");
		Map<String,String> linkedServiceMap2 = jedis.hgetAll("linkedPlanServices_"+linkPlanServId2+"_linkedService");
		Map<String,String> planserviceCostShareMap1 = jedis.hgetAll("linkedPlanServices_"+linkPlanServId1+"_planserviceCostShare");
		Map<String,String> planserviceCostShareMap2 = jedis.hgetAll("linkedPlanServices_"+linkPlanServId2+"_planserviceCostShare");
		
		JSONObject planJson = new JSONObject(planMap);
		JSONObject planCostShareJson = new JSONObject(planCostShareMap);
		JSONObject linkedPlanServiceJson1 = new JSONObject(linkedPlanServiceMap1);
		JSONObject linkedPlanServiceJson2 = new JSONObject(linkedPlanServiceMap2);
		JSONObject linkedServiceJson1 = new JSONObject(linkedServiceMap1);
		JSONObject linkedServiceJson2 = new JSONObject(linkedServiceMap2);
		JSONObject planserviceCostShareJson1 = new JSONObject(planserviceCostShareMap1);
		JSONObject planserviceCostShareJson2 = new JSONObject(planserviceCostShareMap2);
		
		
		linkedPlanServiceJson1.put("linkedService", linkedServiceJson1);
		linkedPlanServiceJson1.put("planserviceCostShares", planserviceCostShareJson1);
		System.out.println("(((((((((((((((((((((((("+planId+")))))))))))))))))))))))))))");
		linkedPlanServiceJson2.put("linkedService", linkedServiceJson2);
		linkedPlanServiceJson2.put("planserviceCostShares", planserviceCostShareJson2);
		
		JSONArray linkPlanServArr = new JSONArray();
		linkPlanServArr.put(linkedPlanServiceJson1);
		linkPlanServArr.put(linkedPlanServiceJson2);
		
		
		planJson.put("linkedPlanServices", linkPlanServArr);
		planJson.put("planCostShares", planCostShareJson);*/
		
		response.setHeader("ETag", planJson.getString("etag"));
		
		return planJson.toString();
		
		} 
		
		JSONObject jsonMessage = new JSONObject();
		jsonMessage.put("message", "Cannot find any entry with the specified id");
		response.setStatus(HttpServletResponse.SC_NOT_FOUND);
		return jsonMessage.toString();
		
	}
	
	
	@RequestMapping(method=RequestMethod.PUT, value="/updatePlan/{id}")
	public String updatePlanedService(HttpServletRequest request,@PathVariable(value = "id") String planId, HttpServletResponse response, @RequestBody String jsondata) throws ProcessingException, IOException, ParseException, NoSuchAlgorithmException, JSONException, java.text.ParseException  {
	
		
		final String secretKey = "AbditSecretKey";
		
		if(request.getHeader("Authorization") == null) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			JSONObject errMessage = new JSONObject();
        	errMessage.put("message", "Authorization failed!");
        	return errMessage.toString();
		}
		if(request.getHeader("If-Match") == null) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			JSONObject errMessage = new JSONObject();
        	errMessage.put("message", "No etag found");
        	return errMessage.toString();
		}
		
		
		String header  = request.getHeader("Authorization");
		if(header.contains("Bearer") && header != null) {
			String token = header.split(" ")[1];
			String decryptedToken = AES.decrypt(token, secretKey);
			
			try{
			JSONObject js = new JSONObject(decryptedToken);
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			
			Calendar cal = Calendar.getInstance();
		    Date currentDate = cal.getTime();
		    
		    Date date1 = dateFormat.parse((String) js.get("expiry")); 
		    
		    if(currentDate.compareTo(date1) > 0) {
		    	response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				JSONObject errMessage = new JSONObject();
	        	errMessage.put("message", "Token expired!");
	        	return errMessage.toString();
		    } 
		    
			}
			catch(Exception e) {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				JSONObject errMessage = new JSONObject();
	        	errMessage.put("message", "Authorization failed!");
	        	return errMessage.toString();
			}
			
		} else {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			JSONObject errMessage = new JSONObject();
        	errMessage.put("message", "Authorization failed!");
        	return errMessage.toString();
		}
		

		String headerEtag = request.getHeader("If-Match");
		
		
		Set<String> keySet = jedis.keys("*");
		
		if(!keySet.contains(planId)) {
			response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
			JSONObject jsonMessage = new JSONObject();
			jsonMessage.put("message", "Unable to find Plan ID");
			return jsonMessage.toString();
		}
		
		String existingEtag = (jedis.hgetAll(planId)).get("etag").toString();
		
		if(!headerEtag.equals(existingEtag)) {
			response.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
			JSONObject jsonMessage = new JSONObject();
			jsonMessage.put("message", "Plan not updated, please get the latest plan and then update");
			return jsonMessage.toString();
		}
		
		
		
		
		JSONObject jsonObject = new JSONObject(jsondata);
		
		
        
        System.out.println("@@@@@@@@@@@@@@@@@@@"+jsonObject+"@@@@@@@@@@@@@@@@@@@");
        
    
        
        File file = new File("C:\\Users\\Surabhi Patil\\Desktop\\NEU\\NEU ABDIT\\RedisProject\\schema.json");
        
        if (ValidationUtils.isJsonValid(file, jsondata)){
        	String[] resArr = readJson(jsonObject);
        	JSONObject jsonMessage = new JSONObject();
        	jsonMessage.put("message", "The json data was updated and successfully uploaded to redis");
        	jsonMessage.put("planId", resArr[1]);
        	jsonMessage.put("etag", resArr[0]);
        	return jsonMessage.toString();
	    }else{
	    	JSONObject jsonMessage = new JSONObject();
        	jsonMessage.put("message", "Data validation failed!");
        	response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        	return jsonMessage.toString();
	    }
	}
	
	
	@RequestMapping(method=RequestMethod.DELETE, value="/deletePlan/{id}")
	public String delete(HttpServletRequest request, HttpServletResponse response ,@PathVariable(value = "id") String id) throws ProcessingException, IOException, JSONException, java.text.ParseException  {
	
		final String secretKey = "AbditSecretKey";
		
		if(request.getHeader("Authorization") == null) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			JSONObject errMessage = new JSONObject();
        	errMessage.put("message", "Authorization failed!");
        	return errMessage.toString();
		}
		
		String header  = request.getHeader("Authorization");
		if(header.contains("Bearer") && header != null) {
			String token = header.split(" ")[1];
			String decryptedToken = AES.decrypt(token, secretKey);
			
			try{
			JSONObject js = new JSONObject(decryptedToken);
			DateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
			
			Calendar cal = Calendar.getInstance();
		    Date currentDate = cal.getTime();
		    
		    Date date1 = dateFormat.parse((String) js.get("expiry")); 
		    
		    if(currentDate.compareTo(date1) > 0) {
		    	response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				JSONObject errMessage = new JSONObject();
	        	errMessage.put("message", "Token expired!");
	        	return errMessage.toString();
		    } 
		    
			}
			catch(Exception e) {
				response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
				JSONObject errMessage = new JSONObject();
	        	errMessage.put("message", "Authorization failed!");
	        	return errMessage.toString();
			}
			
		} else {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			JSONObject errMessage = new JSONObject();
        	errMessage.put("message", "Authorization failed!");
        	return errMessage.toString();
		}
		
		JSONObject jsonMessage = new JSONObject();
		Boolean foundFlag = false;
		Set<String> keySet = jedis.keys("*");
		
		for(String key : keySet) {
			if(id.equals(key)) {
				foundFlag = true;
				System.out.println("in equals");
			
				if(id.split("_").length > 2) {
				if(id.split("_")[2].equals("linkedPlanServices")) {
				if(jedis.smembers(id).size() > 0) {
				System.out.println("I am in if");
				for(String smemKey : jedis.smembers(id)) {
					System.out.println("I am in for");
					jedis.del(smemKey);
				}
				jedis.del(key);
				jsonMessage.put("message", "Key deleted successfully");
				
			}
				}
				}else {
				jedis.del(key);
				jsonMessage.put("message", "Key deleted successfully");
			}
			}
			
		}
		
		if(!foundFlag) {
			response.setStatus(HttpServletResponse.SC_NOT_FOUND);
			jsonMessage.put("message", "Deletion failed, Key not found!");
		}
		
		return jsonMessage.toString();
	
	}
	
}
