import com.cycling74.max.*;

import java.io.File;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

public class ScoreTranscription extends MaxObject
{
    private ArrayList<StringBuilder> trigLists = new ArrayList<>();

    private int[] nbHits;
    private int minVel = 1;

    private int nbEvents = 0;
    private boolean hasTriggers = false;
    private StringBuilder s;
    private boolean increment = false;


    private static final String[] INLET_ASSIST = new String[]{
            "Master MIDI data", "MIDI data to sync (delay by 10ms)", "Manual Sync Points"
    };

    private static final String[] OUTLET_ASSIST = new String[]{
            "Master channel Note Count", "Trigger Sequence Index"
    };

    public ScoreTranscription(Atom[] args)
    {
        declareInlets(new int[]{DataTypes.ALL, DataTypes.ALL, DataTypes.ALL});
        declareOutlets(new int[]{DataTypes.LIST, DataTypes.INT});

        setInletAssist(INLET_ASSIST);
        setOutletAssist(OUTLET_ASSIST);

        nbHits = new int[127];
        this.reset();
    }

    public void setMinVel(int val){
        this.minVel = val;
    }


    public void reset(){
        post("Resetting all note counters to zero");
        for(int i = 0; i < nbHits.length; i++)
            nbHits[i] = 0;
        nbEvents = 0;
        hasTriggers = false;
        trigLists.clear();
        trigLists.add(new StringBuilder());
        writeOutlets();

    }

    private void writeOutlets(){
        outlet(0, nbHits);
        outlet(1, trigLists.size()-1);
    }

    public void list(Atom[] list)
    {
        // If the note is coming from the "master" channel 1
        if(getInlet()==0){
        if((list.length == 2) && (list[0].isInt()) && (list[1].isInt())) {

            int note = list[0].getInt();
            int vel = list[1].getInt();

            if(vel >= this.minVel) {
                // Deal with the last note stuff: if we found triggers, send the string out.
                if(hasTriggers || increment){
                    //s.append("test");
                    if(increment)
                        s.append(" next");
                    s.append(";\n");
                    trigLists.get(trigLists.size()-1).append(s);
                }

                // See if we need to reset the counters and stuff...
                if (increment){
                    for(int i = 0; i < nbHits.length; i++)
                        nbHits[i] = 0;
                    //post(trigLists.get(trigLists.size()-1).toString());
                    trigLists.add(new StringBuilder());
                    increment = false;
                }

                // Increment the note counter
                nbHits[note] += 1;

                // Prepare the string with the number and real note content
                s = new StringBuilder();
                s.append(nbEvents);
                s.append(", ");
                s.append("realnote ");
                s.append(note);
                s.append(" ");
                s.append(nbHits[note]);

                hasTriggers = false;
            }
        }
        writeOutlets();
    }
        // If the note is coming from the "slave" channel 2
        else if (getInlet()==1){
            if((list.length == 2) && (list[0].isInt()) && (list[1].isInt())) {
                int note = list[0].getInt();
                int vel = list[1].getInt();

                // Make sure we have an anchor note so that we can write here
                if(!s.toString().isEmpty()) {
                    // If this if the first note to be added
                    if (!hasTriggers) {
                        s.append(" trigger");
                        hasTriggers = true;
                        nbEvents++;
                    }

                    s.append(" ");
                    s.append(note);
                    s.append(" ");
                    s.append(vel);
                }
            }
        }
    }

    public void bang()
    {
        // Bang on the third inlet means commit previous stuff to file and start a new one
        if (getInlet()==2) {
            post("bang! next");
            //s.append("next");
            increment = true;
        }
    }

    public void write(String relPath) throws IOException {
        post("Now to write the trigger files to disk.");

        String mainPatcherPath = this.getParentPatcher().getFilePath();
        File baseDir = new File(new File(mainPatcherPath).getParent());
        //post("Patch creator based at : " + baseDir.getPath());

        File child = new File(baseDir, relPath);

        if (!child.exists()) {
            if (child.mkdirs()) {
                post("Saving files to directory : " + child.getPath());
            } else {
                post("Failed to create directory");
            }
        }
        for(int i = 0; i < trigLists.size();i++){
            StringBuilder sb = trigLists.get(i);
            String fname = "";
            if(i<10)
                fname = "seq_0"+i;
            else
                fname = "seq_"+i;
            if (!sb.toString().isEmpty()){
                post("Final path : " + new File(child,fname).toString());
                writeToFile(new File(child,fname).toString(), sb.toString());
            }
            else {
                post("The trigger file is empty, not writing to disk");
            }
        }
    }

    private void writeToFile(String filename, String s) throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter(filename));
        writer.write(s);
        writer.close();
    }
}
