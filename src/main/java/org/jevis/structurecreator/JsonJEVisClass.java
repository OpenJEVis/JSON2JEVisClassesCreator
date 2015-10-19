/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jevis.structurecreator;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author ait-user
 */
public class JsonJEVisClass extends org.jevis.commons.json.JsonJEVisClass{
    private long operation;
    private List<JsonJEVisClass> children = new ArrayList<>();
    private List<JsonJEVisClass> validParents = new ArrayList<>();
    
    public long getOperation() {
        return operation;
    }
    public List<JsonJEVisClass> getChildren() {
        return children;
    }
    public List<JsonJEVisClass> getValidParents() {
        return validParents;
    }
}
