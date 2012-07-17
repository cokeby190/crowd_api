package com.nuslivinglab.api;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Normalizer;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import sg.edu.nus.idmi.amilab.db.DBFactory;
import sg.edu.nus.idmi.amilab.db.Database;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class crowd_api extends HttpServlet {

	private static final long serialVersionUID = 1L;

	String mode;

	String location, cam_no, time, metric, path;
	private String distinct_query ="", query_key_str="*";
	
	Statement st;
	
	//constructor
    public crowd_api() { }
    
	@Override
	protected void doGet(HttpServletRequest req, HttpServletResponse resp) 
		throws ServletException, IOException    {
		location = req.getParameter("location");
		cam_no = req.getParameter("cam_no");
		time = req.getParameter("time");
		metric = req.getParameter("metric");
		path = req.getParameter("path");
		String output = req.getParameter("output");
		String distinct = req.getParameter("distinct");
		String query_key = req.getParameter("query_key");
		mode = req.getParameter("mode");
		
		Database db = DBFactory.loadDatabase("com.nuslivinglab.db.db_pgsql");
		st = db.getStatement();
		
		PrintWriter out = resp.getWriter();
		
		if (distinct != null) {
			distinct_query = distinct; 
		}
		
		if (query_key != null) {
			query_key_str = query_key; 
		}
		
		//output in json format
		if(output!=null && output.equals("json")) {
			JsonArray jArray = new JsonArray();
			Gson gson = new GsonBuilder().serializeNulls().create();
			String query;
			
			query = query_process();
							
			try {
				ResultSet result = st.executeQuery(query);
				if(result.next()) {
					do {
						JsonObject obj = new JsonObject();
						obj.addProperty("location", result.getString("location"));
						obj.addProperty("cam_no", result.getString("cam_no"));
						obj.addProperty("time", result.getString("time"));
						obj.addProperty("crowd_metric", result.getString("crowd_metric"));
						obj.addProperty("snapshot_path", result.getString("snapshot_path"));
						jArray.add(obj);
					} while (result.next());
					
				} else {
					JsonObject obj = new JsonObject();
					obj.addProperty("error_msg", "Your Search did not match any food stalls!):");
					jArray.add(obj);
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
			
			resp.setContentType("application/json");
			out.print(gson.toJson(jArray));
			
			reset();
			
		} else {
			resp.setContentType("text/xml");
			out.println("<response>");
				
			String query;
			
			query = query_process();
					
			out.println(query);
			out.println(query_key_str);
			
			try {
				ResultSet result = st.executeQuery(query);

				if (query_key_str.equals("*")) {
					while(result.next()) {
						out.println("<camera>");
						out.println("<location>" + process_string(result.getString("location")) + "</location>");
						out.println("<cam_no>" + process_string(result.getString("cam_no")) + "</cam_no>");
						out.println("<time>" + process_string(result.getString("time")) + "</time>");
						out.println("<crowd_metric>" + process_string(result.getString("crowd_metric")) + "</crowd_metric>");
						out.println("<snapshot_path>" + process_string(result.getString("snapshot_path")) + "</snapshot_path>");
						out.println("</camera>");
					} //else {
					//	out.println("<errormessage>Your Search did not match any food stalls!):</errormessage>");
					//}
				} else if (!query_key_str.equals("*")) {
					while(result.next()) {
						out.println("<food_list>");
						//out.println("<canteen_name>" + process_string(result.getString("canteen_name")) + "</canteen_name>");
						//out.println("<" + query_key_str + ">" + process_string(result.getString("canteen_name")) + "</" + query_key_str + ">");
						out.println("<" + query_key_str + ">" + process_string(result.getString(query_key_str)) + "</" + query_key_str + ">");
						out.println("</food_list>");
					}
				}
				
				int size =0;
				if (result != null) 
				{
				  result.beforeFirst();
				  result.last();
				  size = result.getRow();
				}
				
				out.println(size);
				
				if (size == 0) {
					out.println("<errormessage>Your Search did not match any food stalls!):</errormessage>");
				}
				
			} catch (SQLException e) {
				e.printStackTrace();
				out.println(e.toString());
			}
			out.println("</response>");
			
			reset();
		}
		db.close();
	}
	
	public static String process_string(String text) {
		//remove accents
		text = Normalizer.normalize(text, Normalizer.Form.NFD);
		
		Pattern pattern = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
	    String remove = pattern.matcher(text).replaceAll("");
		
		//replace '&' with '&amp;'
		text = remove.replace("&", "&amp;");
		
		return text;
	}
	
	private String query_process() {
		String query = null;
		String query_check = null;
		
		if(mode.equals("Update")) {
			
			query_check = "SELECT COUNT(id) FROM crowd_density.wte_cam_va WHERE location=" + location 
							+ " AND cam_no=" + cam_no;
			
			try {
				ResultSet result = st.executeQuery(query_check);
				result.last();
				int rowcount = result.getRow();
				
				if(rowcount == 0) {
					query = "INSERT INTO crowd_density.wte_cam_va (location, cam_no, time, crowd_metric, snapshot_path) " +
								"VALUES ('" + location + "', '" + 
								cam_no + "', '" + time + "', '" + metric + "', '" + path + "')"; //ADD new item
					
				} else if(rowcount == 1){
						query = "UPDATE crowd_density.wte_cam_va SET time = " + time + ", crowd_metric = " + metric + 
								 ", snapshot_path = "+ path + " WHERE location = " + location + " AND cam_no = " + cam_no; //UPDATE existing item
				}
				
			} catch (SQLException e) {
				e.printStackTrace();
			}
			
			
		} else if(mode.equals("Select")) {
			query = "SELECT " + distinct_query + " " + query_key_str + " FROM crowd_density.wte_cam_va";
		} 
		
		return query;
	}
	
	private void reset() {
		
	}
}
