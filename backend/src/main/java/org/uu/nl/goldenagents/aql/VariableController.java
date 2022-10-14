package org.uu.nl.goldenagents.aql;

import org.apache.jena.sparql.core.Var;

import java.util.HashMap;
import java.util.UUID;

/**
 * The VariableController is used when translating an AQL Query to ARQ, to construct fresh variables
 */
public class VariableController {

    private HashMap<String, Integer> labelStore;
    private int labellessIndex;
    private Var focusVariable;
    private UUID focusID;

    /**
     * Default constructor
     */
    public VariableController() {
        this.labelStore = new HashMap<>();
        labellessIndex = 0;
    }

    /**
     * Create the next variable name for a given label, which has not been used before
     *
     * @param label     Label to create variable for
     * @return          ARQ Var for label which has not been used before
     */
    public Var getVariableForLabel(String label) {
        if(!this.labelStore.containsKey(label)) {
            this.labelStore.put(label, 0);
        }
        int index = this.labelStore.get(label);
        this.labelStore.put(label, index+1);
        return getVariableForLabel(label, index);
    }

    /**
     * Create the n'th (index) variable name for a given label
     *
     * @param label     Given label
     * @param index     Given index (n)
     * @return          ARQ Var
     */
    public Var getVariableForLabel(String label, int index) {
        if(index == 0) {
            return Var.alloc(label);
        } else {
            return Var.alloc(String.format("%s_%s", label, indexToVariable(index)));
        }
    }

    /**
     * Create the next unlabeled ARQ Var.
     * This method automatically increases the index, so is not without side effects
     * @return  The next ARQ Var
     */
    public Var getVariable() {
        int index = this.labellessIndex;
        this.labellessIndex++;
        return this.getVariableForIndex(index);
    }

    /**
     * Create an ARQ variable object for a given index
     * @param index     Index used for deterministic variable name creation
     * @return          ARQ Var
     */
    public Var getVariableForIndex(int index) {
        return Var.alloc(indexToVariable(index));
    }

    /**
     * Explicitly set a variable as having the focus
     * @param var   ARQ Var that has the active focus
     */
    public void setFocusVariable(Var var) {
        this.focusVariable = var;
    }

    /**
     * Get the ARQ Var with the current focus. May be null
     * @return  ARQ Var
     */
    public Var getFocusVariable() {
        return this.focusVariable;
    }

    /**
     * Create a deterministic string of a letter and a number based on some index. Used as affix to distinguish variable
     * names for the same resource
     * @param index     Index to use for variable affix creation
     * @return          Deterministic string of letter and number
     */
    private static String indexToVariable(int index) {
        int letter = Math.floorMod(index, 26);
        int number = Math.floorDiv(index, 26);
        return String.format("%s%s", String.valueOf((char)(letter + 97)), number > 0 ? String.valueOf(number) : "");
    }

    public void setQueryFocusID(UUID focus) {
        this.focusID = focus;
    }

    public UUID getQueryFocusID() {
        return this.focusID;
    }

}
