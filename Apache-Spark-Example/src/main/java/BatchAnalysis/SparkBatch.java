package BatchAnalysis;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.lang.*;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Encoder;
import org.apache.spark.sql.Encoders;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.callUDF;
import static org.apache.spark.sql.functions.dayofmonth;
import static org.apache.spark.sql.functions.dayofyear;
import static org.apache.spark.sql.functions.month;
import static org.apache.spark.sql.functions.weekofyear;
import static org.apache.spark.sql.functions.count;
import static org.apache.spark.sql.functions.countDistinct;
import static org.apache.spark.sql.functions.explode;
import static org.apache.spark.sql.functions.split;
import static org.apache.spark.sql.functions.substring;
import static org.apache.spark.sql.functions.sum;
import static org.apache.spark.sql.functions.row_number;
import org.apache.spark.sql.expressions.Window;
import org.apache.spark.sql.expressions.WindowSpec;

public class SparkBatch {
	
	public static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

	public static void main(String[] args) {
		
		SparkSession spark = SparkSession
				  .builder()
				  .appName("BatchAnalysis")
				  .getOrCreate();
		
		//#################################    UDFs    ##################################
		
		spark.udf().register("isDummy", (String s) -> {
			if (s==null || s.isEmpty() || s.length()==0) return false;
			char c = s.charAt(0);
			if (c<33 || c>126) return false;
			return true;
		}, DataTypes.BooleanType); 
		
		spark.udf().register("classify", (String s) -> {
			char c = s.charAt(0); 
			if ( c>=65 && c<=90) 
				return "" + c;
			else if (c>=97 && c<=122  ) 
				return "" + (char)(c-32);
			else if (c>=48 && c<=57)
				return  "0Number";
			else 
				return "!Symbol"; 
		}, DataTypes.StringType);
		
		//#################################    loading data    ##################################
		// Encoders are created for Java beans
		Encoder<SearchEntry> entryEncoder = Encoders.bean(SearchEntry.class);
		
		Dataset<SearchEntry> entryDS = spark.read()  //de fainetai diafora me th dikh mas ektelesh ths askhshs h xrhsh datasets enanti df
			    .option("delimiter", "\t")
			    .option("header", "true")
			    .csv("hdfs:/user/nickiemand16/" + args[0])
			    .as(entryEncoder);
		
		entryDS.printSchema();
		
		Dataset<Row> entryDF = spark.read()  //de fainetai diafora me th dikh mas ektelesh ths askhshs h xrhsh datasets enanti df
			    .option("delimiter", "\t")
			    .option("header", "true")
			    .option("inferSchema", "true")
			    .csv("hdfs:/user/nickiemand16/" + args[0]);
		
		entryDF.printSchema();
		
		Dataset<Row> wikiDF = spark.read() //inferring the schema
				.option("header", "true")
				.csv("hdfs:/user/nickiemand16/" + args[1]);
		
		Dataset<Row> stopDF = spark.read() 
				.option("header", "true")
				.csv("hdfs:/user/nickiemand16/" + args[2]);
		
		//#################################    2.1    ##################################
		/*
		//Dataset<Row> yearDS = entryDS.withColumn("day", callUDF("extractYear",col("date"))); // example of using udf
		Dataset<Row> newDS = entryDS
				.withColumn("dayofyear", dayofyear(col("date")))
				.withColumn("weekofyear", weekofyear(col("date")))
				.withColumn("month", month(col("date")))
				.persist();
		
		Dataset<Row> dayDS = newDS.groupBy("dayofyear").agg(count("*").as("SearchesPerDay")).orderBy("dayofyear");

		dayDS.show(366,false); //try with map function ?
		
		Dataset<Row> weekDS = newDS.groupBy("weekofyear").agg(count("*").as("SearchesPerWeek")).orderBy("weekofyear");
		
		weekDS.show(52,false); 
		
		Dataset<Row> monthDS = newDS.groupBy("month").agg(count("*").as("SearchesPerMonth")).orderBy("month");

		monthDS.show(12,false); // me xrhsh udf boroume na apeikonizoyme kalytera thn sthlh ths hmeromhnias
		*/
		
		//#################################    2.2    ##################################
		Dataset<SearchEntry> succEntryDS = entryDS.filter(col("pos").isNotNull());
		
		long success = succEntryDS.count();
		long entries = entryDS.count();
		/*System.out.println("Total searches: " + entries);
		System.out.println("Success searches percentage: " + success * 100.0 / entries + " %");
		System.out.println("Unsuccess searches percentage: " + (entries - success) * 100.0 / entries + " %");
		 */
		
		//#################################    2.3    ##################################
		/*
		Dataset<Row> urlDS = succEntryDS.groupBy("url").agg(countDistinct(col("userid")).as("distinctVisitors")) //anti diplou group by poy xrisimopoioysame paliotera
				.filter(col("distinctVisitors").$greater(10))
				.orderBy(col("distinctVisitors").desc());
	
		urlDS.show(100,false);   // System.out.println("Count pages" + urlDS.count()); ~15 xil selides deixnoume top 100
 		*/	
		//#################################    2.4    ##################################
		/*
		Dataset<Row> keywordDS = entryDS.select("userid","keywords")
				.withColumn("keywords",explode(split(col("keywords")," ")))
				.groupBy("keywords").agg(count("*").as("Apperances"))
				.orderBy(col("Apperances").desc());
				
		keywordDS.show(50,false);
		
		System.out.println("Distinct keywords" + keywordDS.count());
		*/
		//#################################    2.5.1    ##################################
		
		Dataset<Row> wikiWordDF = wikiDF.withColumn("title", explode(split(col("title"),"_")))
			.filter( callUDF("isDummy",col("title"))).persist();
		/*
		Dataset<Row> wikiFilterDF = wikiWordDF
			.withColumn("firstLetter", substring(col("title"),0,1))
			.withColumn("firstLetter", callUDF("classify",col("firstLetter")))
			.groupBy("firstLetter").agg(count("*").as("apperances"));
		
		WindowSpec window = Window.rowsBetween(Window.unboundedPreceding(),Window.unboundedFollowing()); 
		//gia to warning sxetika me to oti den yparxei partition, de mporoume na kanoume kati, 
		//de xreiazomaste partition se kapoia sthlh afou theloume aggregate se olo to pinaka alliws ola 100%
		
		wikiFilterDF.withColumn("frequency", (col("apperances").multiply(100.0F)).divide(sum(col("apperances")).over(window)))
			.orderBy("firstLetter").show(28,false);
		*/
		//#################################    2.5.2    ##################################		
		
		Dataset<Row> wikiWords = wikiWordDF.orderBy("title").persist();
		/*
		wikiWords.show(400);
		wikiWords.repartition(10) //creates only 10 partitions
			.write()
		    .option("header", "true")
		    .option("delimiter", "\t")
		    .csv("wikiWords.tsv");
		*/
		//#################################    2.6    ##################################	
		
		Dataset<Row> wikiDistinct = wikiWords.dropDuplicates();
		
		WindowSpec window = Window.orderBy("userid"); //row_number() requires window to be ordered
		
		Dataset<Row> keywordDS = entryDS.withColumn("id", row_number().over(window)) 
				.select("id","keywords")
				.withColumn("title",explode(split(col("keywords")," "))) 
				.drop("keywords")
				.filter(callUDF("isDummy",col("title")));
		
		keywordDS.show(1000,false);
		
		Dataset<Row> noStopDF = keywordDS.join(stopDF,col("title").equalTo(col("stopWords")),"left_anti");
		
		System.out.println("Exploded entries nonStop: " + noStopDF.count());
		
		Dataset<Row> finalDF = noStopDF.join(wikiDistinct, "title")
				.dropDuplicates("id");
		
		//finalDF.show(1000,false);
		//System.out.println("Searches with results from Wikipedia: " + finalDF.count());
		System.out.println("Persentage of searches with results from Wikipedia: " + 
				+ finalDF.count() *100.0/ noStopDF.dropDuplicates("id").count());
	}
}