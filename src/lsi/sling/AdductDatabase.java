package lsi.sling;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.opencsv.CSVReader;
import expr.Expr;
import expr.Parser;
import expr.SyntaxException;
import expr.Variable;

import java.io.*;
import java.nio.file.FileAlreadyExistsException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * This class handles the database of possible adducts. Specifically, it deals with creating, reading, and writing the
 * database. In order to increase performance, when a database is created, all of the adducts are written into different
 * files based on their charge. This allows the read function to only read data for a specific charge, which allows the
 * program to only read in the relevant adducts. This optimisation Also significantly improves memory management.
 * @author Adithya Diddapur
 */
public class AdductDatabase {

    private static ArrayListMultimap<Integer, Adduct> multimap; //multimap used to cache the adduct information when it is read in

    /**
     * Reads in the data for a specific charge from the file which was created in @createDatabase(String folder)
     *
     * @param folder The folder of the file created in @createDatabase(String folder)
     * @param charge The adduct charge to read in from the folder
     * @return An ArrayList contining the data from the file
     * @throws IOException If there is an error reading from the file
     */
    static List<Adduct> readDatabase(String folder, int charge) throws IOException {
        System.out.println("Reading in Database for charge = " + charge);
        //streams to read the data in
        FileInputStream fin = new FileInputStream(new File(folder + File.separator + charge + ".adduct"));
        ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(fin));

        List<Adduct> data = null; //initialies the variable outside the scope of the try-catch
        try {
            data = (List<Adduct>) ois.readObject();
        } catch (ClassNotFoundException e) {
            System.out.println("ClassNotFoundException e @ readDatabase line 43");
            e.printStackTrace();
        }
        ois.close();
        fin.close();
        System.out.println("Finished Reading Database for charge = " + charge);
        return data;
    }

    /**
     * This method checks to see if a file containing the output list from createListOfAdducts already exists at the
     * given location. If it does not exist, the method calls createListOfAdducts and stores it in a new file at the
     * given location
     *
     * @param folder       The address of the file to check for
     * @param adductFile   The location of the .csv file containing the adduct information
     * @param compoundFile The location of the .cssv file containing all the possible compounds
     * @return 1 if the file already exists. 0 if a new file was created
     * @throws IOException If there is an error creating the file
     */
    static int createDatabase(String folder, String adductFile, String compoundFile) throws IOException {
        multimap = ArrayListMultimap.create(); //initialises the multimap for use in the mapCluster method
        if (!new File(folder).exists()) { //creates a folder to store all the files for each specific charge
            System.out.println("Database does not exist");
            System.out.println("Creating Database now");
            if(!new File(folder).mkdirs()){
                throw new FileNotFoundException();
            }
            List<Adduct> data = createListOfAdducts(adductFile, compoundFile); //computes the actual list of adducts
            //creates a multimap ordered by charge to make it easy to retrieve ALL the adducts for a specific charge
            ListMultimap<Integer, Adduct> multimap = Multimaps.index(
                    data,
                    Adduct::getIonCharge
            );
            //writes the data for each individual charge to a different file
            //doing this allows for caching upon reading
            int[] keys = Arrays.stream(multimap.keySet().toArray()).mapToInt(i -> (int) i).toArray();
            for (int key : keys) {
                File file = new File(folder + File.separator + key + ".adduct");
                if(!file.createNewFile()){
                    throw new FileAlreadyExistsException(file.getAbsolutePath());
                }
                BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
                ObjectOutputStream oos = new ObjectOutputStream(bos);
                oos.writeObject(multimap.get(key));
                bos.close();
                oos.close();
            }
            System.out.println("Finished Creating Database");
            return 0; //returns 0 if a new database was created
        } else {
            System.out.println("Database already exists");
            return 1; //returns 1 if the file already exists
        }
    }

    /**
     * This method reads in the "raw" csv files (containing ion and compound information)  and combines them. To do this, it
     * parses the expression in the ion file and uses that to calculate the resultant m/z for each combination. This data
     * is then stored in a List of Adduct objects. Note that this method executes the method concurrently for each possibility
     * to speed up processing time. In case an interruptedException is thrown by the ExecutorService, it is caught and handled
     * within this method
     *
     * @param adductF   The location of the .csv file containing the adduct information
     * @param compoundF The location of the .cssv file containing all the possible compounds
     * @return A List of Adduct objects
     * @throws IOException If there is an error reading the files
     */
    static private List<Adduct> createListOfAdducts(String adductF, String compoundF) throws IOException {
        List<Adduct> temp = Collections.synchronizedList(new ArrayList<Adduct>());
        File adductFile = new File(adductF);
        File compoundFile = new File(compoundF);
        CSVReader adductReader = new CSVReader(new BufferedReader(new FileReader(adductFile)));
        String[] nextLineCompound;
        try { //to catch the InterruptedException thrown by executor.awaitTermination
            ExecutorService executor = Executors.newCachedThreadPool(); //to parallelise the task
            //loop to iterate through the adductFile and read the contents iteratively
            for (String[] adductInfo : adductReader) {
                String expression = adductInfo[2];
                String ionName = adductInfo[1];
                if (!expression.equals("Ion mass")) { //to ignore the first (title) line
                    //parses all relevant values for later use
                    double ionMass = Double.parseDouble(adductInfo[5]);
                    String icharge = adductInfo[3];
                    icharge = (icharge.charAt(icharge.length() - 1) + icharge);
                    icharge = icharge.substring(0, icharge.length() - 1);
                    int ionCharge = Integer.parseInt(icharge);
                    //CSVReader to read the compounds in
                    //The nested loop ensures that every possible combination of adduct and compound is computed
                    CSVReader compoundReader = new CSVReader(new BufferedReader(new FileReader(compoundFile)));
                    while ((nextLineCompound = compoundReader.readNext()) != null) {
                        if (!nextLineCompound[0].equals("")) {
                            String massString = nextLineCompound[1];
                            String compoundFormula = nextLineCompound[0];
                            String compoundCommonName = nextLineCompound[2];
                            String compoundSystemicName = nextLineCompound[3];
                            if (!massString.equals("exactMass")) {
                                //creates a runnable for each combination for the sake of concurrency
                                Runnable task = () -> {
                                    Expr expr = null;
                                    try {
                                        expr = Parser.parse(expression);
                                    } catch (SyntaxException e) {
                                        e.printStackTrace();
                                    }
                                    //calls functionality from the expr package to parse the expressions
                                    Variable M = Variable.make("M");
                                    M.setValue(Double.parseDouble(massString));
                                    if (expr != null) {
                                        temp.add(new Adduct(ionName, expression, ionMass, ionCharge, Double.parseDouble(massString), expr.value(), compoundFormula, compoundCommonName, compoundSystemicName));
                                    }
                                };

                                executor.submit(task);
                            }
                        }
                    }
                }
            }
            executor.shutdown();
            executor.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            System.out.println("InterruptedException e @ createListOfAdducts line 150");
            e.printStackTrace();
        }
        return temp;
    }

    /**
     * This method maps each peakcluster in the input peakClusterList, to the peakClusterList of possible adducts it could be.
     *
     * @param file The MzXMLFile to map
     * @param dir The location of the adductDatabase folder
     * @return A modified version of the input list containing the possible adducts
     * @throws IOException Thrown if there is an error reading in the database
     */
    static ArrayList<PeakCluster> mapClusters(MzXMLFile file, String dir) throws IOException {
        ArrayList<PeakCluster> peakClusterList = file.getPeakClusters();
        //wrapper to catch the InterruptedException thrown by the ExecutorService on termination
        try {
            ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
            for (PeakCluster cluster : peakClusterList) {
                //reads in the data for that particular charge if it hasn't already been read (and cached)
                //if the data hasn't already been read, it is cached in the multimap
                if (!multimap.keySet().contains(cluster.getCharge())) {
                    multimap.putAll(cluster.getCharge(), AdductDatabase.readDatabase(dir, cluster.getCharge()));
                }
                //Runnable to map the adducts
                Runnable task = () -> cluster.findAdducts(multimap.get(cluster.getCharge()).stream()
                        .filter(p -> p.getIonCharge() == cluster.getCharge())
                        .collect(Collectors.toList()));
                executorService.submit(task);
            }
            executorService.shutdown();
            executorService.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            System.out.println("InterruptedException e @ mapClusters line 182");
            e.printStackTrace();
        }
        return peakClusterList;
    }

    static void mapClusters(AlignedPeakCluster alignedPeakCluster, String dir) throws IOException {
        ExecutorService executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        //reads in the data for that particular charge if it hasn't already been read (and cached)
        //if the data hasn't already been read, it is cached in the multimap
        if(!multimap.keySet().contains(alignedPeakCluster.getCharge())){
            multimap.putAll(alignedPeakCluster.getCharge(), AdductDatabase.readDatabase(dir, alignedPeakCluster.getCharge()));
        }
        //Runnable to map the adducts
        Runnable task = () -> alignedPeakCluster.findAdducts(multimap.get(alignedPeakCluster.getCharge()).stream()
                .filter(p -> p.getIonCharge() == alignedPeakCluster.getCharge())
                .collect(Collectors.toList()));
        executorService.submit(task);
        executorService.shutdown();
        try {
            executorService.awaitTermination(Integer.MAX_VALUE, TimeUnit.DAYS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        //return alignedPeakCluster;
    }
}
