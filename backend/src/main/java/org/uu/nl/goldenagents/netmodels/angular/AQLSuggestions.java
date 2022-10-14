package org.uu.nl.goldenagents.netmodels.angular;

import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.OntResource;
import org.apache.jena.rdf.model.impl.ResourceImpl;
import org.apache.jena.vocabulary.RDFS;
import org.uu.nl.net2apl.core.fipa.acl.FIPASendableObject;
import org.uu.nl.net2apl.core.platform.Platform;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

public class AQLSuggestions implements Serializable, FIPASendableObject {

    private List<TypeSuggestion> classList;
    private List<TypeSuggestion> propertyList;
    private List<InstanceSuggestion> instanceList;
    private UUID focusID;

    /**
     * Naively construct suggestion objects from lists of classes, properties and instances.
     *
     * This creates the hierarchy from <i>all</i> passed concepts, without filtering out classes that have no place here.
     * Use with care.
     *
     * @param queryID       Query focus ID for which these suggestions apply
     * @param classList     List of classes that can be suggested
     * @param propertyList  List of properties that can be suggested
     * @param instanceList  List of instances that can be suggested
     */
    public AQLSuggestions(UUID queryID, List<OntClass> classList, List<OntProperty> propertyList, List<ResourceImpl> instanceList) {
        this.focusID = queryID;
        this.classList = classList.stream().filter(x -> !x.isAnon() && !x.hasSuperClass()).map(x -> new TypeSuggestion(x, Collections.emptySet())).collect(Collectors.toList());
        this.propertyList = propertyList.stream().map(x -> new TypeSuggestion(x, false, Collections.emptySet())).collect(Collectors.toList());
        this.propertyList.addAll(propertyList.stream().map(x -> new TypeSuggestion(x, true, Collections.emptySet())).collect(Collectors.toList()));
        this.instanceList = instanceList.stream().map(InstanceSuggestion::new).collect(Collectors.toList());
    }

    /**
     * Default constructor for AQL Query Suggestions
     * @param classList     List of classes to suggest
     * @param propertyList  List of properties to suggest
     * @param instanceList  List of instances to suggest
     * @param queryID       Query focus ID for which these suggestions apply
     */
    public AQLSuggestions(List<TypeSuggestion> classList, List<TypeSuggestion> propertyList, List<InstanceSuggestion> instanceList, UUID queryID) {
        this.focusID = queryID;
        this.classList = classList;
        this.propertyList = propertyList;
        this.instanceList = instanceList;
    }

    public List<TypeSuggestion> getClassList() {
        return classList;
    }

    public void setClassList(List<TypeSuggestion> classList) {
        this.classList = classList;
    }

    public List<TypeSuggestion> getPropertyList() {
        return propertyList;
    }

    public void setPropertyList(List<TypeSuggestion> propertyList) {
        this.propertyList = propertyList;
    }

    public List<InstanceSuggestion> getInstanceList() {
        return instanceList;
    }

    public void setInstanceList(List<InstanceSuggestion> instanceList) {
        this.instanceList = instanceList;
    }

    public UUID getFocusID() {
        return focusID;
    }

    public void setFocusID(UUID focusID) {
        this.focusID = focusID;
    }

    public static class TypeSuggestion implements Serializable {
        private boolean forwardjump;
        private String url;
        private String type;
        private String id;
        private String label;
        private List<TypeSuggestion> subitems = new ArrayList<>();

        public TypeSuggestion(OntClass ontClass, Set<OntClass> allowedClasses) {
            populateFromResource(ontClass);
            this.forwardjump = false;
            this.type = "class";
            addSubitems(ontClass, allowedClasses);
        }

        private void addSubitems(OntClass currentItem, Set<OntClass> allowedClasses) {
            currentItem.listSubClasses(true).forEachRemaining(subclass -> {
                if(allowedClasses.isEmpty() || allowedClasses.contains(subclass))
                    this.subitems.add(new TypeSuggestion(subclass, allowedClasses));
                else
                    addSubitems(subclass, allowedClasses);
            });
        }

        private void addSubitems(OntProperty currentItem, boolean forwards, Set<OntProperty> allowedProperties) {
            currentItem.listSubProperties(true).forEachRemaining(subProperty -> {
                if(allowedProperties.isEmpty() || allowedProperties.contains(subProperty))
                    this.subitems.add(new TypeSuggestion(subProperty.asProperty(), forwards, allowedProperties));
                else
                    addSubitems(subProperty.asProperty(), forwards, allowedProperties);
            });
        }

        /**
         * Construct a new Type Suggestion from an OntProperty
         * @param ontProperty           OntProperty that can be suggested
         * @param forwardjump              Boolean indicating if the property is forward crossing (p of q) or backward
         *                              crossing (p : q)
         * @param allowedProperties     A set of properties occurring in the ontology (i.e. allowed properties that
         *                              the system can handle
         */
        public TypeSuggestion(OntProperty ontProperty, boolean forwardjump, Set<OntProperty> allowedProperties) {
            populateFromResource(ontProperty);
            this.forwardjump = forwardjump;
            this.type = "prop";

            addSubitems(ontProperty, forwardjump, allowedProperties);
        }

        private void populateFromResource(OntResource resource) {
            this.url = resource.getURI();
            this.id = "random. DO we need this?";
            this.label = resource.getLabel("en");
            this.label = this.label == null ? resource.getLocalName() : this.label;
        }

        public boolean isForwardjump() {
            return forwardjump;
        }

        public String getUrl() {
            return url;
        }

        public String getType() {
            return type;
        }

        public String getId() {
            return id;
        }

        public String getLabel() {
            return label;
        }

        public List<TypeSuggestion> getSubitems() {
            return subitems;
        }
    }

    public static class InstanceSuggestion implements Serializable {
        private String label;
        private String target;
        private String id;
        private String title;

        public InstanceSuggestion(ResourceImpl individual) {
            this.target = individual.getURI();
            this.label = this.setLabel(individual);
            this.id = "random. DO we need this?";
            this.title = this.target;
        }

        private String setLabel(ResourceImpl individual) {
            // TODO ideally we use rdfs:label, but this property needs to be included in each DB query if we want to extract it
            String label;
            if(individual.hasProperty(RDFS.label)) {
                label = individual.getProperty(RDFS.label).getString();
            } else if(!individual.getLocalName().isEmpty()) {
                label = individual.getLocalName();
            } else {
                String uri = individual.getURI();
                String nameSpace = individual.getNameSpace();
                if(nameSpace.equalsIgnoreCase(uri)) {
                    label = uri.substring(uri.lastIndexOf("/") + 1);
                } else {
                    label = uri.substring(nameSpace.length());
                }
            }
            Platform.getLogger().log(getClass(), "URI " + individual.getURI() + " now has label " + label);
            return label;
        }

        public String getLabel() {
            return label;
        }

        public String getTarget() {
            return target;
        }

        public String getId() {
            return id;
        }

        public String getTitle() {
            return title;
        }
    }
}
