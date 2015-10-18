/**
 * Copyright (C) 2015 Werner Lamprecht
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
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jevis.api.JEVisClass;
import org.jevis.api.JEVisClassRelationship;
import org.jevis.api.JEVisConstants;
import org.jevis.api.JEVisDataSource;
import org.jevis.api.JEVisException;
import org.jevis.api.JEVisRelationship;
import org.jevis.api.JEVisType;
import org.jevis.api.sql.JEVisDataSourceSQL;
import org.jevis.commons.json.JsonFactory;
import org.jevis.commons.json.JsonObject;
import org.jevis.commons.json.JsonType;


public class JSON2JEVisClassesCreator {
    private interface OPERATIONS {
        int IGNORE = -1;
        int CREATE = 0; // or update
        int DELETE = -2;
        int RENAME = -3;
    }
    /**
     * The JEVisDataSource is the central class handling the connection to the
     * JEVis Server
     */
    private static JEVisDataSource _jevis_ds;
    
    /**
     * Example how to use JSON2JEVisClassesCreator
     *
     * @param args not used
     */
    public static void main(String[] args){
        JSON2JEVisClassesCreator wsc = new JSON2JEVisClassesCreator();
        wsc.connectToJEVis("localhost", "3306", "jevis", "jevis", "jevistest", "Sys Admin", "jevis");
        try {
            wsc.createStructure();
        } catch (JEVisException ex) {
            Logger.getLogger(JSON2JEVisClassesCreator.class.getName()).log(Level.SEVERE, null, ex);
        } catch (IOException ex) {
            Logger.getLogger(JSON2JEVisClassesCreator.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    /**
     * 
     * Creates the JEVis-Classes for SQL Server
     * 
     */
    public void createStructure() throws JEVisException, IOException{
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String input = new String(Files.readAllBytes(Paths.get("SQLClasses.json")), StandardCharsets.UTF_8);
        
        JsonJEVisClass root = gson.fromJson(input, JsonJEVisClass.class);
        System.out.println(root.getOperation() + ":" + root.getName());
        
        for(JsonJEVisClass parent : root.getChildren()) {
            createClass(parent, root);
        }
        
        
        // Pretty-print current JEVis build
        //JEVisClass channelClass = _jevis_ds.getJEVisClass("Data Source");
        //System.out.println(gson.toJson(JsonFactory.buildJEVisClassComplete(channelClass)));
        
        //PrimitiveType
        //PrimitiveType.STRING = 0;
        //PrimitiveType.DOUBLE = 1;
        //PrimitiveType.LONG = 2;
        //PrimitiveType.FILE = 3;
        //PrimitiveType.BOOLEAN = 4;
        
        
    }
        
    /**
     * Create a class under the given parent class
     * @param className Name of the class to create.
     * @param parentName Name of the parent-class.
     * @param types Array of names for the types/attributes to create.
     * @param validParents Array of names of valid parents for the new class.
     * @return The newly created class, null if the class already existed.
     * @throws JEVisException
     */
    public JEVisClass createClass(JsonJEVisClass jevisClass, JsonJEVisClass parent)
            throws JEVisException {
        int relInherit = JEVisConstants.ClassRelationship.INHERIT;
        int relDir = JEVisConstants.Direction.FORWARD;
        int relValidParent = JEVisConstants.ClassRelationship.OK_PARENT;
        
        String className = jevisClass.getName();
        String parentName = parent.getName();
        System.out.println(String.format("Processing class/parent: '%s/%s'",
                className, parentName));
        
        int op = jevisClass.getOperation();
        if (op == OPERATIONS.IGNORE) {
            for (JsonJEVisClass child : jevisClass.getChildren()) {
                createClass(child, jevisClass);
            }
            return null;
        } else if (op == OPERATIONS.DELETE) {
            // TODO: delete class
            System.out.println("Error: operation delete class not implemented. Class: " + jevisClass.getName());
            return null;
        } else if (op == OPERATIONS.RENAME) {
            // TODO: rename class
            System.out.println("Error: operation rename class not implemented. Class: " + jevisClass.getName());
            return null;
        }
        // else { // op == OPERATIONS.CREATE
         
        
        // get parent
        JEVisClass parentClass = _jevis_ds.getJEVisClass(parent.getName());
        if (parentClass == null) {
            System.out.println(String.format("Error: Cant find parent JEVisClass: '%s'",
                    parentName));
            return null;
        }
        
        JEVisClass newJClass = null;
        // Delete Class and its children if it exists
        newJClass = _jevis_ds.getJEVisClass(className);
        if (newJClass != null) {
            deleteClassRec(newJClass);
        }
        // create new class
        System.out.println("Create Class: " + className);
        newJClass = _jevis_ds.buildClass(className);
        // Link new class under parent
        newJClass.buildRelationship(parentClass, relInherit, relDir);
        
        // Set the icon from the parent
        newJClass.setIcon(parentClass.getIcon());
        
        // Add valid parents
        for (JsonJEVisClass vp : jevisClass.getValidParents()) {
            newJClass.buildRelationship(_jevis_ds.getJEVisClass(vp.getName()),
                    relValidParent, relDir);
        }
        
        // Create the types/attributes for the new class
        // TODO: delete old types
        List<JsonType> types = jevisClass.getTypes();
        if (types == null)
            types = new ArrayList<>();
        for (JsonType type : types) {
            String typeName = type.getName();
            System.out.println(String.format("In class '%s' create new type '%s'",
                    className, typeName));
            JEVisType newType = newJClass.buildType(typeName);
            newType.setPrimitiveType(type.getPrimitiveType());
        }
        
        // Commit the changes
        newJClass.commit();
        
        // Create children
        for (JsonJEVisClass child : jevisClass.getChildren()) {
            createClass(child, jevisClass);
        }
        return newJClass;
        
    }
    public void deleteClassRec(JEVisClass jevisClass) throws JEVisException {
        String className = jevisClass.getName();
        System.out.println("Delete Class enter recursion: " + className);
        
        // Delete children first
        List<JEVisClassRelationship> childRels = jevisClass.getRelationships(JEVisConstants.ClassRelationship.INHERIT, JEVisConstants.Direction.FORWARD);
        for (JEVisClassRelationship rel : childRels) {
            JEVisClass child = rel.getOtherClass(jevisClass);
            deleteClassRec(child);
        }
        
        deleteClass(jevisClass);
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
    
    public JEVisClass createClassbak(String className, String parentName,
            String[] types, String[] validParents) throws JEVisException {
        int relInherit = JEVisConstants.ClassRelationship.INHERIT;
        int relDir = JEVisConstants.Direction.FORWARD;
        int relValidParent = JEVisConstants.ClassRelationship.OK_PARENT;
        
        // Only create class if it does not exist
        if (_jevis_ds.getJEVisClass(className) != null) {
            System.out.println(
                String.format("Class '%s' already exists, not changing the classes", className));
            return null;
        }
        
        // get parent
        JEVisClass parentClass = _jevis_ds.getJEVisClass(parentName);
        // create new class
        JEVisClass newJClass = _jevis_ds.buildClass(className);
        // Link new class under parent
        newJClass.buildRelationship(parentClass, relInherit, relDir);
        // Set the icon from the parent
        newJClass.setIcon(parentClass.getIcon());
        
        // Add valid parents
        if (validParents == null)
            validParents = new String[] {};
        for (String vp : validParents)
            newJClass.buildRelationship(_jevis_ds.getJEVisClass(vp), relValidParent, relDir);
        
        // Create the types/attributes for the new class
        if (types == null)
            types = new String[] {};
        for (String t : types) {
            newJClass.buildType(t);
        }
        
        // Commit the changes
        newJClass.commit();
        return newJClass;
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
