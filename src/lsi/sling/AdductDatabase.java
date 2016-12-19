package lsi.sling;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.opencsv.CSVReader;
import expr.Expr;
import expr.Parser;
import expr.SyntaxException;
import expr.Variable;

import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * This class handles the database of possible adducts. Specifically, it deals with creating, reading, and writing the
 * database. In order to increase performance, when a database is created, all of the adducts are written into different
 * files based on their charge. This allows the read function to only read data for a specific charge, which allows the
 * program to only read in the relevant adducts. This optimisation Also significantly improves memory management.
 * @author Adithya Diddapur
 */
public class AdductDatabase {

    /**
     * Reads in the data from the file which was created in @createDatabase(String folder)
     * @param folder The folder of the file created in @createDatabase(String folder)
     * @return An ArrayList contining the data from the file
     * @throws IOException If there is an error reading from the file
     * @throws ClassNotFoundException If there is an error converting the object to an ArrayList<Adduct>
     */
    static List<Adduct> readDatabase(String folder, int charge) throws IOException, ClassNotFoundException {
        System.out.println("Reading in Database for charge = " + charge);
        FileInputStream fin = new FileInputStream(new File(folder + File.separator + charge + ".adduct"));
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(fin));
        List<Adduct> data = (List<Adduct>) ois.readObject();
        fin.close();
        System.out.println("Finished Reading Database for charge = " + charge);
        return data;
    }

    /**
     * This method checks to see if a file containing the output list from createListOfAdducts already exists at the
     * given location. If it does not exist, the method calls createListOfAdducts and stores it in a new file at the
     * given location
     * @param folder The address of the file to check for
     * @return 1 if the file already exists. 0 if a new file was created
     * @throws IOException If there is an error creating the file
     */
    static int createDatabase(String folder, String adductFile, String compoundFile) throws IOException, InterruptedException {
        if(!new File(folder).exists()){
            System.out.println("Database does not exist");
            System.out.println("Creating Database now");
            new File(folder).mkdirs();
            List<Adduct> data = createListOfAdducts(adductFile, compoundFile);
            ListMultimap<Integer,Adduct> multimap = Multimaps.index(
                    data,
                    adduct -> adduct.getIonCharge()
            );
            int[] keys = Arrays.stream(multimap.keySet().toArray()).mapToInt(i -> (int)i).toArray();
            for(int key : keys){
                File file = new File(folder + File.separator + key + ".adduct");
                file.createNewFile();
                FileOutputStream fos = new FileOutputStream(file);
                ObjectOutputStream oos = new ObjectOutputStream(fos);
                oos.writeObject(multimap.get(key));
                oos.close();
            }
            data = null;
            multimap = null;
            System.gc();
            System.out.println("Finished Creating Database");
            return 0;
        } else {
            System.out.println("Database already exists");
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
    static private List<Adduct> createListOfAdducts(String adductF, String compoundF) throws IOException, InterruptedException {
        //List<Adduct> temp = Collections.synchronizedList(new ArrayList());
        List<Adduct> temp = Collections.synchronizedList(new ArrayList<Adduct>());
        //ArrayList temp = new ArrayList();
        File adductFile = new File(adductF);
        File compoundFile = new File(compoundF);
        CSVReader adductReader = new CSVReader(new BufferedReader(new FileReader(adductFile)));
        //CSVReader compoundReader = new CSVReader(new FileReader(compoundFile));
        String[] nextLineCompound;

        ExecutorService executor = Executors.newCachedThreadPool();
        //ExecutorService executor = Executors.newWorkStealingPool();

        //ArrayList<Double> expressions = new ArrayList();
        for (String[] adductInfo : adductReader) {
            String expression = adductInfo[2];
            String ionName = adductInfo[1]; //this line works
            if (!expression.equals("Ion mass")) {
                double ionMass = Double.parseDouble(adductInfo[5]); //this line works
                String icharge = adductInfo[3]; //this line works
                icharge = (icharge.charAt(icharge.length() - 1) + icharge); //this line works
                icharge = icharge.substring(0, icharge.length() - 1); //this line works
                int ionCharge = Integer.parseInt(icharge); //this line works
                CSVReader compoundReader = null;
                compoundReader = new CSVReader(new BufferedReader(new FileReader(compoundFile)));
                while ((nextLineCompound = compoundReader.readNext()) != null) {
                    if (!nextLineCompound[0].equals("")) {
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
                                try {
                                    expr = Parser.parse(expression);
                                } catch (SyntaxException e) {
                                    e.printStackTrace();
                                }
                                Variable M = Variable.make("M");
                                M.setValue(Double.parseDouble(massString));
                                //temp.add(new Adduct(adductInfo[1],expression,Double.parseDouble(adductInfo[5]),Integer.parseInt(adductInfo[3]),Double.parseDouble(massString),expr.value(),compoundInfo[0],compoundInfo[2],compoundInfo[3]));
                                //temp.add(expr.value());
                                if (expr != null) {
                                    temp.add(new Adduct(ionName, expression, ionMass, ionCharge, Double.parseDouble(massString), expr.value(), compoundFormula, compoundCommonName, compoundSystemicName));
                                }
                                //System.out.println(expr.value()); //this line does NOT work
                            };

                            executor.submit(task);
                        }
                    }
                }
            }
        }
        executor.shutdown();
        executor.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
        System.gc();
        return temp;
    }
}
