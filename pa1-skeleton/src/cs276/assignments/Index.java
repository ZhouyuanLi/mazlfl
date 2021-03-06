package cs276.assignments;

import cs276.util.Pair;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.LinkedList;
import java.util.List;

public class Index {

	// Term id -> (position in index file, doc frequency) dictionary
	private static Map<Integer, Pair<Long, Integer>> postingDict 
		= new TreeMap<Integer, Pair<Long, Integer>>();
	// Doc name -> doc id dictionary
	private static Map<String, Integer> docDict
		= new TreeMap<String, Integer>();
	// Term -> term id dictionary
	private static Map<String, Integer> termDict
		= new TreeMap<String, Integer>();
	private static Map<Integer, ArrayList<Integer>> MapOfTermIdDocId
	  = new TreeMap<Integer, ArrayList<Integer>>();
	// Block queue
	private static LinkedList<File> blockQueue
		= new LinkedList<File>();

	// Total file counter
	private static int totalFileCount = 0;
	// Document counter
	private static int docIdCounter = 0;
	// Term counter
	private static int wordIdCounter = 0;
	// Index
	private static BaseIndex index = null;

	
	public static PostingList readPosting(FileChannel fc) throws IOException {
	  PostingList result = null;
	  try {
	    result = index.readPosting(fc);
	  } catch (Throwable e) {
	    throw new IOException(e);
	  }
	  return result;
	}

	/* 
	 * Write a posting list to the given file 
	 * You should record the file position of this posting list
	 * so that you can read it back during retrieval
	 * 
	 * */
	private static void writePosting(FileChannel fc, PostingList posting)
			throws IOException {
		/*
		 * TODO: Your code here
		 *	 
		 */
	}

	public static void main(String[] args) throws IOException {
		/* Parse command line */
		if (args.length != 3) {
			System.err
					.println("Usage: java Index [Basic|VB|Gamma] data_dir output_dir");
			return;
		}

		/* Get index */
		String className = "cs276.assignments." + args[0] + "Index";
		try {
			Class<?> indexClass = Class.forName(className);
			index = (BaseIndex) indexClass.newInstance();
		} catch (Exception e) {
			System.err
					.println("Index method must be \"Basic\", \"VB\", or \"Gamma\"");
			throw new RuntimeException(e);
		}

		/* Get root directory */
		String root = args[1];
		File rootdir = new File(root);
		if (!rootdir.exists() || !rootdir.isDirectory()) {
			System.err.println("Invalid data directory: " + root);
			return;
		}

		/* Get output directory */
		String output = args[2];
		File outdir = new File(output);
		if (outdir.exists() && !outdir.isDirectory()) {
			System.err.println("Invalid output directory: " + output);
			return;
		}

		if (!outdir.exists()) {
			if (!outdir.mkdirs()) {
				System.err.println("Create output directory failure");
				return;
			}
		}

		/* A filter to get rid of all files starting with .*/
		FileFilter filter = new FileFilter() {
			@Override
			public boolean accept(File pathname) {
				String name = pathname.getName();
				return !name.startsWith(".");
			}
		};

		/* BSBI indexing algorithm */
		File[] dirlist = rootdir.listFiles(filter);

		/* For each block */
		for (File block : dirlist) {
			File blockFile = new File(output, block.getName());
			blockQueue.add(blockFile);

			File blockDir = new File(root, block.getName());
			File[] filelist = blockDir.listFiles(filter);
			
			/* For each file */
			for (File file : filelist) {
				++totalFileCount;
				String fileName = block.getName() + "/" + file.getName();
				docDict.put(fileName, docIdCounter++);
				
				BufferedReader reader = new BufferedReader(new FileReader(file));
				String line;
				Set<Integer> termIdAppeared = new HashSet<Integer>();
				while ((line = reader.readLine()) != null) {
					String[] tokens = line.trim().split("\\s+");
					for (String token : tokens) {
					  int termId = 0;
					  if (!termDict.containsKey(token)) {
					    termId = wordIdCounter;
					    wordIdCounter++;
					    termDict.put(token, termId);
					  } else {
					    termId = termDict.get(token);
					  }
					  if (!MapOfTermIdDocId.containsKey(termId)) {
					    MapOfTermIdDocId.put(termId, new ArrayList<Integer>());
					  }
					  if (!termIdAppeared.contains(termId)) {
					    termIdAppeared.add(termId);
					    MapOfTermIdDocId.get(termId).add(docIdCounter - 1);
					  }
						/*
						 * TODO(zhouyuanl): done.
						 */
					}
				}
				reader.close();
			}

			/* Sort and output */
			if (!blockFile.createNewFile()) {
				System.err.println("Create new block failure.");
				return;
			}
			
			RandomAccessFile bfc = new RandomAccessFile(blockFile, "rw");
			
			/*
			 * TODO: Your code here
			 *       Write all posting lists for all terms to file (bfc) 
			 */
			
			bfc.close();
		}

		/* Required: output total number of files. */
		System.out.println(totalFileCount);

		/* Merge blocks */
		while (true) {
			if (blockQueue.size() <= 1)
				break;

			File b1 = blockQueue.removeFirst();
			File b2 = blockQueue.removeFirst();
			
			File combfile = new File(output, b1.getName() + "+" + b2.getName());
			if (!combfile.createNewFile()) {
				System.err.println("Create new block failure.");
				return;
			}

			RandomAccessFile bf1 = new RandomAccessFile(b1, "r");
			RandomAccessFile bf2 = new RandomAccessFile(b2, "r");
			RandomAccessFile mf = new RandomAccessFile(combfile, "rw");

			PostingList postingList1 = readPosting(bf1.getChannel());
			PostingList postingList2 = readPosting(bf2.getChannel());
			while(postingList1 != null && postingList2 != null) {
			  int termId1 = postingList1.getTermId();
			  int termId2 = postingList2.getTermId();
			  if (termId1 < termId2) {
			    writePosting(mf.getChannel(), postingList1);
			    postingList1 = readPosting(bf1.getChannel());
			  } else if (termId1 > termId2) {
			    writePosting(mf.getChannel(), postingList2);
			    postingList2 = readPosting(bf2.getChannel());
			  } else {
			    PostingList mergedPostingList = new PostingList(termId1);
			    List<Integer> list1 = postingList1.getList();
			    int size1 = list1.size();
			    List<Integer> list2 = postingList2.getList();
			    int size2 = list2.size();
			    List<Integer> mergedList = mergedPostingList.getList();
			    int index1 = 0;
			    int index2 = 0;
			    while(index1 < size1 && index2 < size2) {
			      int docId1 = list1.get(index1);
			      int docId2 = list2.get(index2);
			      if (docId1 < docId2) {
			        mergedList.add(docId1);
			        index1++;
			      } else {
			        mergedList.add(docId2);
			        index2++;
			      }
			    }
			    
			    while(index1 < size1) {
            int docId1 = list1.get(index1);
            mergedList.add(docId1);
            index1++;
          }
			    
			    while(index2 < size2) {
			      int docId2 = list2.get(index2);
			      mergedList.add(docId2);
            index2++;
			    }

			    writePosting(mf.getChannel(), mergedPostingList);
			    postingList1 = readPosting(bf1.getChannel());
			    postingList2 = readPosting(bf2.getChannel());
			  }
			}
			while(postingList1 != null) {
			  writePosting(mf.getChannel(), postingList1);
        postingList1 = readPosting(bf1.getChannel());
			}
			while(postingList2 != null) {
        writePosting(mf.getChannel(), postingList2);
        postingList2 = readPosting(bf2.getChannel());
      }
			  
			
			 
			/*
			 * TODO(zhouyuanl): done.
			 */
			
			bf1.close();
			bf2.close();
			mf.close();
			b1.delete();
			b2.delete();
			blockQueue.add(combfile);
		}

		/* Dump constructed index back into file system */
		File indexFile = blockQueue.removeFirst();
		indexFile.renameTo(new File(output, "corpus.index"));

		BufferedWriter termWriter = new BufferedWriter(new FileWriter(new File(
				output, "term.dict")));
		for (String term : termDict.keySet()) {
			termWriter.write(term + "\t" + termDict.get(term) + "\n");
		}
		termWriter.close();

		BufferedWriter docWriter = new BufferedWriter(new FileWriter(new File(
				output, "doc.dict")));
		for (String doc : docDict.keySet()) {
			docWriter.write(doc + "\t" + docDict.get(doc) + "\n");
		}
		docWriter.close();

		BufferedWriter postWriter = new BufferedWriter(new FileWriter(new File(
				output, "posting.dict")));
		for (Integer termId : postingDict.keySet()) {
			postWriter.write(termId + "\t" + postingDict.get(termId).getFirst()
					+ "\t" + postingDict.get(termId).getSecond() + "\n");
		}
		postWriter.close();
	}
}
