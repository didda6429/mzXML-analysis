package lsi.sling;

import com.opencsv.CSVReader;
import expr.Expr;
import expr.Parser;
import expr.SyntaxException;
import expr.Variable;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by lsiv67 on 12/12/2016.
 */
public class AdductDatabase {

    /**
     * Reads in the data from the file which was created in @createDatabase(String location)
     * @param location The location of the file created in @createDatabase(String location)
     * @return An ArrayList contining the data from the file
     * @throws IOException If there is an error reading from the file
     * @throws ClassNotFoundException If there is an error converting the object to an ArrayList<Adduct>
     */
    static ArrayList<Adduct> readDatabase(String location) throws IOException, ClassNotFoundException {
        FileInputStream fin = new FileInputStream(new File(location));
        ObjectInputStream ois = new ObjectInputStream(fin);
        ArrayList<Adduct> data = (ArrayList<Adduct>)ois.readObject();
        fin.close();
        return data;
    }

    /**
     * This method checks to see if a file containing the output list from createListOfAdducts already exists at the
     * given location. If it does not exist, the method calls createListOfAdducts and stores it in a new file at the
     * given location
     * @param finalLocation The address of the file to check for
     * @return 1 if the file already exists. 0 if a new file was created
     * @throws IOException If there is an error creating the file
     */
    static int createDatabase(String finalLocation) throws IOException {
        if(!new File(finalLocation).exists()){
            List<Adduct> data = createListOfAdducts();
            ArrayList<Adduct> dat = new ArrayList<Adduct>(data);
            FileOutputStream fos = new FileOutputStream(new File(finalLocation));
            ObjectOutputStream oos = new ObjectOutputStream(fos);
            oos.writeObject(dat);
            oos.close();
            return 0;
        } else {
            return 1; //returns 1 if the file already exists
        }
    }

    /**
     * This method reads in the "raw" csv files (containing ion and compound information)  and combines them. To do this, it
     * parses the expression in the ion file and uses that to calculate the resultant m/z for each combination. This data
     * is then stored in a List of Adduct objects. Note that this method executes the method concurrently for each possibility
     * to speed up processing time
     * @return A List of Adduct objects
     * @throws IOException If there is an error reading the files
     */
    static List<Adduct> createListOfAdducts() throws IOException {
        //List<Adduct> temp = Collections.synchronizedList(new ArrayList());
        List<Adduct> temp = Collections.synchronizedList(new ArrayList());
        //ArrayList temp = new ArrayList();
        File adductFile = new File("C:/Users/lsiv67/Documents/mzXML Sample Data/Adducts.csv");
        File compoundFile = new File("C:/Users/lsiv67/Documents/mzXML Sample Data/Database.csv");
        CSVReader adductReader = new CSVReader(new FileReader(adductFile));
        //CSVReader compoundReader = new CSVReader(new FileReader(compoundFile));
        String[] nextLineCompound;

        ExecutorService executor = Executors.newCachedThreadPool();
        //ExecutorService executor = Executors.newWorkStealingPool();

        Iterator<String[]> adductIterator = adductReader.iterator();
        //ArrayList<Double> expressions = new ArrayList();
        while(adductIterator.hasNext()){
            String[] adductInfo = adductIterator.next();
            String expression = adductInfo[2];
            String ionName = adductInfo[1]; //this line works
            if(!expression.equals("Ion mass")) {
                double ionMass = Double.parseDouble(adductInfo[5]); //this line works
                String icharge = adductInfo[3]; //this line works
                icharge = (icharge.charAt(icharge.length()-1) + icharge); //this line works
                icharge = icharge.substring(0,icharge.length()-1); //this line works
                int ionCharge = Integer.parseInt(icharge); //this line works
                CSVReader compoundReader = null;
                compoundReader = new CSVReader(new FileReader(compoundFile));
                while ((nextLineCompound = compoundReader.readNext()) != null) {
                    if(!nextLineCompound[0].equals("")) {
                        String massString = nextLineCompound[1];
                        String compoundFormula = nextLineCompound[0]; //this line works
                        String compoundCommonName = nextLineCompound[2]; //this line works
                        String compoundSystemicName = nextLineCompound[3]; //this line works
                        if (!massString.equals("exactMass")) {
                            Runnable task = () -> {
                                //String icharge = adductInfo[3];
                                //icharge = (icharge.charAt(icharge.length()-1) + icharge);
                                //icharge = icharge.substring(0,icharge.length()-1);
                                Expr expr = null;
                                try{
                                    expr = Parser.parse(expression);
                                } catch (SyntaxException e) {
                                    e.printStackTrace();
                                }
                                Variable M = Variable.make("M");
                                M.setValue(Double.parseDouble(massString));
                                //temp.add(new Adduct(adductInfo[1],expression,Double.parseDouble(adductInfo[5]),Integer.parseInt(adductInfo[3]),Double.parseDouble(massString),expr.value(),compoundInfo[0],compoundInfo[2],compoundInfo[3]));
                                //temp.add(expr.value());
                                temp.add(new Adduct(ionName,expression,ionMass,ionCharge,Double.parseDouble(massString),expr.value(),compoundFormula,compoundCommonName,compoundSystemicName));
                                //System.out.println(expr.value()); //this line does NOT work
                            };

                            executor.submit(task);
                        }
                    }
                }
            }
        }
        executor.shutdown();
        return temp;
    }
}
