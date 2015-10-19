/**
 * Copyright (C) 2015 Reinhold Gschweicher
 *
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation in version 3.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 *
 * This driver is part of the OpenJEVis project, further project information are
 * published at <http://www.OpenJEVis.org/>.
 */

package org.jevis.structurecreator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisClassRelationship;
import org.jevis.api.JEVisConstants;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisType;
import org.jevis.api.sql.JEVisDataSourceSQL;
import org.jevis.commons.json.JsonType;


public class JSON2JEVisClassesCreator {
    private interface OPERATIONS {
        int IGNORE = -1;
        int CREATE = 0; // or update
        int DELETE = -2;
        int DELETE_RECURSIVE = -3;
        int RENAME = -4;
    }
    /**
     * The JEVisDataSource is the central class handling the connection to the
     * JEVis Server
     */
    private static JEVisDataSource _jevis_ds;
    
    public static void main(String[] args){
        JSON2JEVisClassesCreator wsc = new JSON2JEVisClassesCreator();
        wsc.connectToJEVis("localhost", "3306", "jevis", "jevis", "jevistest", "Sys Admin", "jevis");
        try {
            // Process all given json-files
            if (args.length > 0) {
                for (String jsonFile : args) {
                    wsc.processJSONFile(jsonFile);
                }
            } else { // use defaults
                wsc.processJSONFile("deleteSQLClasses.json");
                wsc.processJSONFile("SQLClasses.json");
            }
            
        } catch (JEVisException ex) {
            Logger.getLogger(JSON2JEVisClassesCreator.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(JSON2JEVisClassesCreator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * 
     * Creates the JEVis-Classes given by jsonFile
     * 
     * @param jsonFile path to file containing JSON-Formated class-description
     */
    public void processJSONFile(String jsonFile) throws JEVisException, IOException{
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String input = new String(Files.readAllBytes(Paths.get(jsonFile)), StandardCharsets.UTF_8);
        
        JsonJEVisClass root = gson.fromJson(input, JsonJEVisClass.class);
        System.out.println(root.getOperation() + ":" + root.getName());
        
        for(JsonJEVisClass parent : root.getChildren()) {
            createClass(parent, root);
        }
        
        // Pretty-print current JEVis build
        //JEVisClass channelClass = _jevis_ds.getJEVisClass("Data Source");
        //System.out.println(gson.toJson(JsonFactory.buildJEVisClassComplete(channelClass)));
        
    }
        
    /**
     * Create a class under the given parent class and all its children.
     * @param jsonClass Structured information for JEVisClass to create.
     * @param parent Structured information of the parent of the class to create.
     * @return the created JEVisClass, null on error
     */
    public JEVisClass createClass(JsonJEVisClass jsonClass, JsonJEVisClass parent)
            throws JEVisException {
        int relInherit = JEVisConstants.ClassRelationship.INHERIT;
        int relDir = JEVisConstants.Direction.FORWARD;
        int relValidParent = JEVisConstants.ClassRelationship.OK_PARENT;
        
        String className = jsonClass.getName();
        String parentName = parent.getName();
        System.out.println(String.format("Processing op/class/parent: '%d/%s/%s'",
                jsonClass.getOperation(), className, parentName));
        
        // Get parent JEVisClass
        JEVisClass parentClass = _jevis_ds.getJEVisClass(parent.getName());
        
        // Get JEVisClass given by jsonClass
        JEVisClass jevisClass = _jevis_ds.getJEVisClass(className);
        
        // Execute specified operation
        boolean createCurrentClass = true;
        int op = jsonClass.getOperation();
        if (op == OPERATIONS.IGNORE) {
            System.out.println("\tIgnore Class: " + className);
            createCurrentClass = false;
        } else if (op == OPERATIONS.DELETE) {
            System.out.println("\tDelete Class: " + className);
            if (jevisClass != null) {
                deleteClass(jevisClass);
            } else {
                System.out.println("\tclass not found, carry on: " + className);
            }
            createCurrentClass = false;
        } else if (op == OPERATIONS.DELETE_RECURSIVE) {
            System.out.println("\tDelete Class recursive: " + className);
            if (jevisClass != null) {
                deleteClassRec(jevisClass);
            } else {
                System.out.println("\tclass not found, carry on: " + className);
            }
            createCurrentClass = false;
        } else if (op == OPERATIONS.RENAME) {
            // TODO: rename class
            System.out.println("Error: operation 'RENAME' not implemented. Class: " + className);
            return null;
        }
        // else { // op == OPERATIONS.CREATE
        
        // Dependent on the previous operation create a new class or not
        if (createCurrentClass) {
            // create new class
            if (jevisClass == null) {
                System.out.println("\tCreate JEVisClass: " + className);
                jevisClass = _jevis_ds.buildClass(className);
                
                // Link new class under parent, except parent is 'oot'
                if (!parentName.equals("root")) {
                    if (parentClass == null) {
                        System.out.println(String.format("Error: Cant find parent JEVisClass: '%s'",
                                parentName));
                        return null;
                    }
                    jevisClass.buildRelationship(parentClass, relInherit, relDir);

                    // Set the icon from the parent
                    // TODO: what icon to use for classes under root?
                    jevisClass.setIcon(parentClass.getIcon());
                }

                // Add valid parents
                for (JsonJEVisClass vp : jsonClass.getValidParents()) {
                    jevisClass.buildRelationship(_jevis_ds.getJEVisClass(vp.getName()),
                            relValidParent, relDir);
                }

                // Create the types/attributes for the new class
                List<JsonType> types;
                if ((types = jsonClass.getTypes()) == null) {
                    types = new ArrayList<>();
                }
                for (JsonType type : types) {
                    //PrimitiveType
                    //PrimitiveType.STRING = 0;
                    //PrimitiveType.DOUBLE = 1;
                    //PrimitiveType.LONG = 2;
                    //PrimitiveType.FILE = 3;
                    //PrimitiveType.BOOLEAN = 4;
                    String typeName = type.getName();
                    System.out.println(String.format("\tcreate new type: class/type '%s'/'%s'",
                            className, typeName));
                    JEVisType newType = jevisClass.buildType(typeName);
                    newType.setPrimitiveType(type.getPrimitiveType());
                }
                // Commit the changes
                jevisClass.commit();

                
            } else {
                System.out.println("\tJEVisClass exists, not changing the class: " + className);
            }
            
        }
        
        // Process jsonClass-children
        for (JsonJEVisClass child : jsonClass.getChildren()) {
            createClass(child, jsonClass);
        }
        return jevisClass;
        
    }
    
    public void deleteClass(JEVisClass jevisClass) throws JEVisException {
        String className = jevisClass.getName();
        System.out.println("Delete Class: " + className);
        // Delete class
        for (JEVisType type : jevisClass.getTypes()) {
            type.delete();
        }
        jevisClass.delete();
    }    
    public void deleteClassRec(JEVisClass jevisClass) throws JEVisException {
        String className = jevisClass.getName();
        System.out.println("Delete Class enter recursion: " + className);
        
        // Delete children first
        for (JEVisClass child : getChildren(jevisClass)) {
            deleteClassRec(child);
        }
        
        deleteClass(jevisClass);
    }
    
    public List<JEVisClass> getChildren(JEVisClass jevisClass) throws JEVisException {
        ArrayList<JEVisClass> children = new ArrayList<>();
        List<JEVisClassRelationship> childRels = jevisClass.getRelationships(JEVisConstants.ClassRelationship.INHERIT, JEVisConstants.Direction.BACKWARD);
        for (JEVisClassRelationship rel : childRels) {
            JEVisClass child = rel.getOtherClass(jevisClass);
            children.add(child);
        }
        return children;
    }
    
    /**
     * 
     * Connect to JEVis
     *
     * @param sqlServer Address of the MySQL Server
     * @param port Port of the MySQL Server, Default is 3306
     * @param sqlSchema Database schema of the JEVis database
     * @param sqlUser MySQl user for the connection
     * @param sqlPW MySQL password for the connection
     * @param jevisUser Username of the JEVis user
     * @param jevisPW Password of the JEVis user
     */
    private void connectToJEVis(String sqlServer, String port, String sqlSchema, String sqlUser, String sqlPW, String jevisUser, String jevisPW) {

        try {
            //Create an new JEVisDataSource from the MySQL implementation 
            //JEAPI-SQl. This connection needs an vaild user on the MySQl Server.
            //Later it will also be possible to use the JEAPI-WS and by this 
            //using the JEVis webservice (REST) as an endpoint which is much
            //saver than using a public SQL-port.
            _jevis_ds = new JEVisDataSourceSQL(sqlServer, port, sqlSchema, sqlUser, sqlPW);

            //authentificate the JEVis user.
            if (_jevis_ds.connect(jevisUser, jevisPW)) {
                Logger.getLogger(JSON2JEVisClassesCreator.class.getName()).log(Level.INFO, "Connection was successful");
            } else {
                Logger.getLogger(JSON2JEVisClassesCreator.class.getName()).log(Level.INFO, "Connection was not successful, exiting app");
                System.exit(1);
            }

        } catch (JEVisException ex) {
            Logger.getLogger(JSON2JEVisClassesCreator.class.getName()).log(Level.SEVERE, "There was an error while connecting to the JEVis Server");
            Logger.getLogger(JSON2JEVisClassesCreator.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
    }
    
}
