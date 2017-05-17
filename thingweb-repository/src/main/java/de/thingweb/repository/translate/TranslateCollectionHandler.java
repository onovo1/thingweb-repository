package de.thingweb.repository.translate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.jena.atlas.lib.StrUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.vocabulary.DC;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.RDFS;

import de.thingweb.repository.Repository;
import de.thingweb.repository.ThingDescriptionUtils;
import de.thingweb.repository.rest.BadRequestException;
import de.thingweb.repository.rest.NotFoundException;
import de.thingweb.repository.rest.UnsupportedFormat;
import de.thingweb.repository.rest.RESTException;
import de.thingweb.repository.rest.RESTHandler;
import de.thingweb.repository.rest.RESTResource;
import de.thingweb.repository.rest.RESTServerInstance;

import org.json.JSONObject;

public class TranslateCollectionHandler extends RESTHandler {

	public TranslateCollectionHandler(List<RESTServerInstance> instances) {
		super("translate", instances);
	}
	
	@Override
	public RESTResource get(URI uri, Map<String, String> parameters) throws RESTException {
	  
		RESTResource resource = new RESTResource(name(uri), this);
		resource.contentType = "application/ld+json";
		
		List<String> translations = new ArrayList<String>();
		String query;
				
		// Return all Translations
		try {
			translations = TranslateUtils.listTranslations("/translate/");
		} catch (Exception e) {
			throw new BadRequestException();
		}
		
		JSONObject root = new JSONObject();
		for(String translation: translations){
			
		    URI translationUri = URI.create(translation);
		    
		    translation = translationUri.getPath();
		    String id = translation.substring(translation.lastIndexOf("/")+1);
		    
            List<String> entries = Arrays.asList(id.split("_"));
            JSONObject obj = TranslateUtils.createObject(entries.get(0), entries.get(1), entries.get(2));
			root.put(translation, obj);
		}
		resource.content = root.toString();
		return resource;
			
	}

	@Override
	public RESTResource post(URI uri, Map<String, String> parameters, InputStream payload) throws RESTException {
		
		RESTResource resource = new RESTResource(name(uri), this);
		boolean registeredTD = false;
		String data = "";
		String source = "";
		String target = "";
		String rt	  = "";
		
		try {
			data = ThingDescriptionUtils.streamToString(payload);
		} catch (IOException e1) {
			e1.printStackTrace();
			throw new BadRequestException();
		}
		
		// GET source and target TD IDs from query parameters
		if (parameters.containsKey("source") && !parameters.get("source").isEmpty() && parameters.containsKey("target")
				&& !parameters.get("target").isEmpty() && parameters.containsKey("rt") && !parameters.get("rt").isEmpty()) {
			source = TranslateUtils.getid(parameters.get("source"));
			target = TranslateUtils.getid(parameters.get("target"));
			rt	   = parameters.get("rt");
		} else {
			throw new BadRequestException();
		}
		
		String id = source + "_" + target + "_" + rt;
		
		// Checking if the translation has already registered in the dataset.
		String registeredTranslation = TranslateUtils.getTranslateFromId(uri, id);
		
		if (!registeredTranslation.isEmpty()){
			throw new BadRequestException();
		}
		
		// to add new translation to the collection
		URI resourceUri = URI.create(normalize(uri) + "/" + id);
		
		Dataset dataset = Repository.get().dataset;
		List<String> keyWords;

		dataset.begin(ReadWrite.WRITE);
		try {
			
			Model tdb = dataset.getDefaultModel();
			tdb.createResource(resourceUri.toString()).addProperty(DC.source, data);
			ThingDescriptionUtils utils = new ThingDescriptionUtils();
			
			String currentDate = utils.getCurrentDateTime(0);
			//tdb.getResource(resourceUri.toString()).addProperty(RDFS.isDefinedBy, id);
			tdb.getResource(resourceUri.toString()).addProperty(DCTerms.created, currentDate);
			tdb.getResource(resourceUri.toString()).addProperty(DCTerms.modified, currentDate);
			
			addToAll("/translate/" + id, new TranslateHandler(id, instances));
			dataset.commit();
			
			
			resource = new RESTResource("/translate/" + id,
					new TranslateHandler(id, instances));
		} catch (Exception e) {
			e.printStackTrace();
			throw new RESTException();
		} finally {
			dataset.end();
		}
		
		//Delete the fail translation lookup if it exist in the database
		TranslateFailLookUpHandler.delete(uri, id);
		
		return resource;
	}
	
	private String normalize(URI uri) {
		if (!uri.getScheme().equals("http")) {
			return uri.toString().replace(uri.getScheme(), "http");
		}
		return uri.toString();
	}
	
	private String name(URI uri) {
		String path = uri.getPath();
		if (path.contains("/")) {
			return path.substring(uri.getPath().lastIndexOf("/") + 1);
		}
		return path;
	}

}