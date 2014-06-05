package zx.soft.hdfs.affinity;

import java.io.IOException;
import java.util.Iterator;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.ByteWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.RawComparator;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;

/**
 * Runs a basic line-counting job. For each line of input, this processes
 * it by counting the number of input lines and outputting them along
 * with their labels. This makes it possible for the next job to 
 * determine the number of data points, and also where each data point
 * falls in the set.
 * 
 * @author wanggang
 *
 */
public class RowNumberJob {

	public final static byte COUNTER_MARKER = (byte) 'T';
	public final static byte VALUE_MARKER = (byte) 'W';

	public static void main(String[] args) {
		if (args.length != 2) {
			System.out.println("[input] [output]");
			System.exit(-1);
		}
		try {
			Configuration conf = new Configuration();
			Path output = new Path(args[1]);
			FileSystem fs = output.getFileSystem(conf);
			if (fs.exists(output)) {
				fs.delete(output, true);
			}
			Job job = Job.getInstance(conf);

			// Necessary to group both counts and values together in
			// the same Reducer.
			job.setGroupingComparatorClass(IndifferentComparator.class);

			// Necessary to correctly partition data to Reducers.
			job.setPartitionerClass(RowNumberWritable.Partitioner.class);

			job.setMapperClass(RowNumberMapper.class);
			job.setMapOutputKeyClass(ByteWritable.class);
			job.setMapOutputValueClass(RowNumberWritable.class);

			job.setReducerClass(RowNumberReducer.class);
			job.setOutputKeyClass(Text.class);
			job.setOutputValueClass(Text.class);

			job.setOutputFormatClass(TextOutputFormat.class);
			FileOutputFormat.setOutputPath(job, output);

			job.setInputFormatClass(TextInputFormat.class);
			FileInputFormat.setInputPaths(job, args[0]);

			job.setJarByClass(RowNumberJob.class);
			job.setNumReduceTasks(10);

			job.waitForCompletion(true);
		} catch (Exception e) {
			System.err.println(e.getMessage());
			e.printStackTrace(System.err);
			System.exit(1);
		}

		System.exit(0);
	}

	static class RowNumberMapper extends Mapper<LongWritable, Text, ByteWritable, RowNumberWritable> {
		private long[] counters;
		private int numReduceTasks;

		private final RowNumberWritable outputValue = new RowNumberWritable();
		private final ByteWritable outputKey = new ByteWritable();

		@Override
		protected void setup(Context context) throws IOException, InterruptedException {
			numReduceTasks = context.getNumReduceTasks();
			counters = new long[numReduceTasks];
			outputKey.set(VALUE_MARKER);
		}

		@Override
		protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
			outputValue.setValue(value);
			context.write(outputKey, outputValue);
			counters[RowNumberWritable.Partitioner.partitionForValue(outputValue, numReduceTasks)]++;
		}

		@Override
		protected void cleanup(Context context) throws IOException, InterruptedException {
			outputKey.set(COUNTER_MARKER);
			for (int c = 0; c < counters.length - 1; c++) {
				if (counters[c] > 0) {
					outputValue.setCounter(c + 1, counters[c]);
					context.write(outputKey, outputValue);
				}
				counters[c + 1] += counters[c];
			}
		}
	}

	static class RowNumberReducer extends Reducer<ByteWritable, RowNumberWritable, Text, Text> {
		private final Text outputKey = new Text();

		@Override
		protected void setup(Context context) throws IOException, InterruptedException {

		}

		@Override
		protected void reduce(ByteWritable key, Iterable<RowNumberWritable> values, Context context)
				throws IOException, InterruptedException {
			Iterator<RowNumberWritable> itr = values.iterator();
			if (!itr.hasNext()) {
				return;
			}

			long offset = 0;
			RowNumberWritable value = itr.next();
			while (itr.hasNext() && value.getCount() > 0) {
				offset += value.getCount();
				value = itr.next();
			}
			outputKey.set(Long.toString(offset++));
			context.write(outputKey, value.getValue());

			while (itr.hasNext()) {
				value = itr.next();
				outputKey.set(Long.toString(offset++));
				context.write(outputKey, value.getValue());
			}
		}
	}

	public static class IndifferentComparator implements RawComparator<ByteWritable> {
		@Override
		public int compare(ByteWritable left, ByteWritable right) {
			return 0;
		}

		@Override
		public int compare(byte[] b1, int s1, int l1, byte[] b2, int s2, int l2) {
			return 0;
		}
	}

}
