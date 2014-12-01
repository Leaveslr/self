import java.math.*;
import java.util.*;
import java.util.Map.Entry;
import java.io.*;
import java.text.DecimalFormat;


public class HDecoder
{
  private static DecimalFormat f3 = new DecimalFormat("###0.000");
  
  public void process(String configFileName,String sourceFileName,String outputFileName) throws Exception{
	    int numParams = 3;
	    int numSentences = countLines(sourceFileName);

	    double[] weights = new double[numParams];
	    String candsFileName = "";
	    int cps = 0;
	    int N = 0;

	    InputStream inStream = new FileInputStream(new File(configFileName));
	    BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

	    String line = inFile.readLine();
	    while (line != null) {
	      if (line.startsWith("cands_file")) {
	        candsFileName = (line.substring(line.indexOf("=")+1)).trim();
	      } else if (line.startsWith("cands_per_sen")) {
	        cps = Integer.parseInt((line.substring(line.indexOf("=")+1)).trim());
	      } else if (line.startsWith("top_n")) {
	        N = Integer.parseInt((line.substring(line.indexOf("=")+1)).trim());
	      } else if (line.startsWith("LM")) {
	        weights[0] = Double.parseDouble((line.substring(2+1)).trim());
	      } else if (line.startsWith("first model")) {
	        weights[1] = Double.parseDouble((line.substring(11+1)).trim());
	      } else if (line.startsWith("second model")) {
	        weights[2] = Double.parseDouble((line.substring(12+1)).trim());
	      } else if (line.startsWith("#")) {
	      } else if (line.length() > 0) {
	        println("Wrong format in config file.");
	        System.exit(1);
	      }
	      line = inFile.readLine();
	    }

	    inFile.close();

	    String[][] candidates = new String[numSentences][cps];
	    double[][][] features = new double[numSentences][cps][numParams];

	    inStream = new FileInputStream(new File(candsFileName));
	    inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

	    for (int i = 0; i < numSentences; ++i) {
	      for (int n = 0; n < cps; ++n) {
	        // read the nth candidate for the ith sentence
	        line = inFile.readLine();

	/*
	line format:

	i ||| words of candidate translation . ||| feat-1_val feat-2_val ... feat-numParams_val .*

	*/

	        line = (line.substring(line.indexOf("|||")+3)).trim(); // get rid of initial text

	        String candidate_str = (line.substring(0,line.indexOf("|||"))).trim();
	        String feats_str = (line.substring(line.indexOf("|||")+3)).trim(); // get rid of candidate string

	        int junk_i = feats_str.indexOf("|||");
	        if (junk_i >= 0) {
	          feats_str = (feats_str.substring(0,junk_i)).trim();
	        }

	        String[] featVal_str = feats_str.split("\\s+");

	        candidates[i][n] = candidate_str;
	        for (int c = 0; c < numParams; ++c) {
	          features[i][n][c] = Double.parseDouble(featVal_str[c]);
	        }

	      }
	    }


	    double[][] scores = new double[numSentences][cps];
	    for (int i = 0; i < numSentences; ++i) {
	      for (int n = 0; n < cps; ++n) {
	        scores[i][n] = 0;
	        for (int c = 0; c < numParams; ++c) {
	          scores[i][n] += weights[c]*features[i][n][c];
	        }
	      }
	    }


	    FileOutputStream outStream = new FileOutputStream(outputFileName, false); // false: don't append
	    OutputStreamWriter outStreamWriter = new OutputStreamWriter(outStream, "utf8");
	    BufferedWriter outFile = new BufferedWriter(outStreamWriter);

	    for (int i = 0; i < numSentences; ++i) {
	      int[] indices = sort(scores,i);
	      for (int n = 0; n < N; ++n) {
	        String str = "" + i + " ||| " + candidates[i][indices[n]] + " |||";
	        for (int c = 0; c < numParams; ++c) {
	          str += " " + f3.format(features[i][indices[n]][c]);
	        }
	        str += " ||| " + f3.format(scores[i][indices[n]]);
	        writeLine(str, outFile);
	      }

	    }

	    outFile.close();

	//    System.exit(0);

	  
  }
 
  /**
   * 解析并给候选列表重新排序
   * @param input
   * @param weight
   * @param cps
   * @param top_n
   * @return
   */
  public List<String>  processLR(List<String> input,List<Double> weight,int cps,int top_n){
	 
	//  List<Map.Entry<Integer,String>> cands = new ArrayList<Map.Entry<Integer,String>>();
	//  List<Map.Entry<Integer,String>> cands_w = new ArrayList<Map.Entry<Integer,String>>();
	  
	  List<List<String>> cands= new ArrayList<List<String>>();
	  List<List<String>> cands_w= new ArrayList<List<String>>();
	  for(int i=0; i< input.size();i++){
		  
		  List<String> cand = new ArrayList<String>();
		  List<String> cand_w = new ArrayList<String>();
		  for(int k=0; (i+k)<input.size() && k<cps;k++){
			 // System.out.println((i+k));
			  String line = input.get(i+k);		 
			  List<String> r = this.ParserLine(line);
			  int id = Integer.parseInt(r.get(0));
		      String candidate_str = r.get(1);
		      String feats_str = r.get(2); // get rid of candidate string

			  cand.add(candidate_str);
			  cand_w.add(feats_str);
		  }
		  i = i + cps;
		  cands.add(cand);
		  cands_w.add(cand_w);
	  }
	  return this.processLR(cands, cands_w, weight, top_n);
	  
  }
 
  /**
   * 给候选列表重新排序
   * @param cands
   * @param cands_w
   * @param weight
   * @param top_n
   * @return
   */
  public List<String> processLR(List<List<String>> cands, List<List<String>> cands_w,List<Double> weight,int top_n){
	  
	   List<String> result = new ArrayList<String>();
	   HashMap<String,Double> score = new HashMap<String,Double>();
	   List<Map.Entry<String,Double>> mappingList = null;
	   
	   for(int i=0; i< cands_w.size(); i++){
		   List<String> cand_w=  cands_w.get(i);
		   List<String> cand=  cands.get(i);
		   //score
		   for(int j=0; j<cand_w.size();j++ ){
			   String line_w = cand_w.get(j);
			   String[] ws = line_w.trim().split(" ");
			   String line_s = cand.get(j);
			   double ss = 0.0;
			   for(int k=0;k<ws.length;k++){
				   ss += Double.parseDouble(ws[k])*weight.get(k);
			   }
			   score.put(i+" ||| "+line_s+" ||| "+line_w, ss);
		   }
		   //sort
			//通过ArrayList构造函数把map.entrySet()转换成list 
			mappingList = new ArrayList<Map.Entry<String,Double>>(score.entrySet()); 
			  //通过比较器实现比较排序 
			Collections.sort(mappingList, new Comparator<Map.Entry<String,Double>>(){ 
			   public int compare(Map.Entry<String,Double> mapping1,Map.Entry<String,Double> mapping2){ 
			       return mapping2.getValue().compareTo(mapping1.getValue()); 
			   } 
			});
			//out
			if(top_n > mappingList.size()){
				top_n = mappingList.size();
			}
			for(int h=0; h<top_n;h++){
				Map.Entry<String,Double> enty= mappingList.get(h);
			    String line = enty.getKey();
			    result.add(line+" ||| "+enty.getValue());
			//    result.add(line);
			}
			mappingList.clear();
			score.clear();
	   }
	   //print
//	   for(int i=0; i<result.size();i++){
//		   System.out.println(result.get(i));
//	   }
	   return result;
	 
   }
  
  public List<String> ParserLine(String line){
	  List<String> result = new ArrayList<String>();
	  int id = Integer.parseInt((line.substring(0,line.indexOf("|||"))).trim());
		 // System.out.println(id);
	  line = (line.substring(line.indexOf("|||")+3)).trim(); // get rid of initial text
	  String candidate_str = (line.substring(0,line.indexOf("|||"))).trim();
	  String feats_str = (line.substring(line.indexOf("|||")+3)).trim(); // get rid of candidate string

	  int junk_i = feats_str.indexOf("|||");
	  if (junk_i >= 0) {
	      feats_str = (feats_str.substring(0,junk_i)).trim();
	  }
	  result.add(Integer.toString(id));
	  result.add(candidate_str);
	  result.add(feats_str);
	  
	  return result;
  }
  public void TestLR(String FileName){
	  List<String> input = new ArrayList<String>();
	  List<Double> weight = new ArrayList<Double>();
	  weight.add(1.0);
	  weight.add(1.0);
	  weight.add(1.0);
	  int cps = 50 ;
	  int top_n = 10;
		try {
			InputStream inStream = new FileInputStream(new File(FileName));
		    BufferedReader inFile = new BufferedReader(new InputStreamReader(inStream, "utf8"));

			 String line ;
			 while ((line = inFile.readLine()) != null) {
				 if(!line.equals("")){
					input.add(line); 
				 }
				// System.out.println(line);
			 }
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		this.processLR(input, weight, cps, top_n);
	   
  }
  public static void main(String[] args) throws Exception
  {
//    String configFileName = args[0];
//    String sourceFileName = args[1];
//    String outputFileName = args[2];
//
//    HDecoder hd = new HDecoder();
//    hd.process(configFileName, sourceFileName, outputFileName);
	  
	  HDecoder hd = new HDecoder();
	  hd.TestLR("F:/java_work/Git/mert/zmert_ex/cand_database.txt");
    

  }

  private static int[] sort(double[][] scores, int i)
  {
    int numCands = scores[i].length;
    int[] retA = new int[numCands];
    double[] sc = new double[numCands];

    for (int n = 0; n < numCands; ++n) {
      retA[n] = n;
      sc[n] = scores[i][n];
    }

    for (int j = 0; j < numCands; ++j) {
      int best_k = j;
      double best_sc = sc[j];
      for (int k = j+1; k < numCands; ++k) {
        if (sc[k] > best_sc) {
          best_k = k;
          best_sc = sc[k];
        }
      }

      // switch j and best_k
      int temp_n = retA[best_k];
      retA[best_k] = retA[j];
      retA[j] = temp_n;

      double temp_sc = sc[best_k];
      sc[best_k] = sc[j];
      sc[j] = temp_sc;
    }

    return retA;
  }

  private static void sort(int[] keys, double[] vals, int start, int end)
  {
    if (end-start > 1) {
      int mid = (start+end)/2;
      sort(keys,vals,start,mid);
      sort(keys,vals,mid+1,end);

    }
  }

  private static int countLines(String fileName) throws Exception
  {
    BufferedReader inFile = new BufferedReader(new FileReader(fileName));

    String line;
    int count = 0;
    do {
      line = inFile.readLine();
      if (line != null) ++count;
    }  while (line != null);

    inFile.close();

    return count;
  }

  private static void writeLine(String line, BufferedWriter writer) throws IOException
  {
    writer.write(line, 0, line.length());
    writer.newLine();
    writer.flush();
  }

  private static void println(Object obj) { System.out.println(obj); }
  private static void print(Object obj) { System.out.print(obj); }

}
