package lsi.sling;

import com.opencsv.CSVReader;
import expr.Expr; //expr library from github.com/darius/expr taken on 12/12/2016 @ 15:10
import expr.Parser;
import expr.SyntaxException;
import expr.Variable;
import org.apache.commons.math3.analysis.function.Add;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by lsiv67 on 12/12/2016.
 */
public class adductDatabase {
    static List createDatabase() throws IOException, ScriptException {
        List temp = Collections.synchronizedList(new ArrayList());
        //ArrayList temp = new ArrayList();
        File adductFile = new File("C:/Users/lsiv67/Documents/mzXML Sample Data/Adducts.csv");
        File compoundFile = new File("C:/Users/lsiv67/Documents/mzXML Sample Data/Database.csv");
        CSVReader adductReader = new CSVReader(new FileReader(adductFile));
        //CSVReader compoundReader = new CSVReader(new FileReader(compoundFile));
        String[] nextLineCompound;

        ExecutorService executor = Executors.newCachedThreadPool();

        Iterator<String[]> adductIterator = adductReader.iterator();
        //ArrayList<Double> expressions = new ArrayList();
        while(adductIterator.hasNext()){
            String[] adductInfo = adductIterator.next();
            String expression = adductInfo[2];
//            String ionName = adductInfo[1];
            if(!expression.equals("Ion mass")) {
//                double ionMass = Double.parseDouble(adductInfo[5]);
//                String icharge = adductInfo[3];
//                icharge = (icharge.charAt(icharge.length()-1) + icharge);
//                icharge = icharge.substring(0,icharge.length()-1);
//                int ionCharge = Integer.parseInt(icharge);
                CSVReader compoundReader = null;
                compoundReader = new CSVReader(new FileReader(compoundFile));
                while ((nextLineCompound = compoundReader.readNext()) != null) {
                    if(!nextLineCompound[0].equals("")) {
                        String massString = nextLineCompound[1];
//                        String compoundFormula = nextLineCompound[0];
//                        String compoundCommonName = nextLineCompound[2];
//                        String compoundSystemicName = nextLineCompound[3];
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
                                temp.add(expr.value());
                                //temp.add(new Adduct(ionName,expression,ionMass,ionCharge,Double.parseDouble(massString),expr.value(),compoundFormula,compoundCommonName,compoundSystemicName));
                                //System.out.println(expr.value());
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
