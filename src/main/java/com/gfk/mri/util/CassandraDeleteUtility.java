package com.gfk.mri.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.security.CodeSource;
import java.util.Date;
import java.util.Properties;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.ResultSetFuture;
import com.datastax.driver.core.Session;
import com.gfk.mri.constants.CassandraTable;
import com.gfk.mri.exception.GfkMriException;
import com.google.common.collect.Sets;

public class CassandraDeleteUtility {

	private String inputCsvFile;
	private String outputCsvFile;
	private CassandraTable table;
	private String keyspace;
	private String host;
	private String username;
	private String password;
	private Integer driverReadTimeout = 12000;
	private Integer concurrentThreadCount = 50;


	public void deleteRecords(Session session)
	{

		String line = "";
		String cvsSplitBy = ",";
		boolean isLongKey = false;
		double totalcount = 0;
		double recordCount = countLines(inputCsvFile);
		BufferedReader br = null;

		CassandraTable table = getTable();
		PreparedStatement ps = null;

		switch (table) {
		case RESPONSES:
			ps = session.prepare("delete from " + getKeyspace() + "." + CassandraTable.getTableName(table) + " where s = ? and d = ?");
			isLongKey = true;

			break;
		case QUESTION_RESPONDENTS:
			ps = session.prepare("delete from " + getKeyspace() + "." + CassandraTable.getTableName(table) + " where s = ? and q = ?");

			break;

		case RESPONDENT_WEIGHT:
			ps = session.prepare("delete from " + getKeyspace() + "." + CassandraTable.getTableName(table) + " where s = ? and wt = ?");
			break;

		default:
			break;
		}

		try
		{
			String outputString = "SID,KeyID,is_deleted\n";
			appendToFile(outputString.toString());
			br = new BufferedReader(new FileReader(inputCsvFile));
			ThreadPoolExecutor executor = (ThreadPoolExecutor) Executors.newFixedThreadPool(getConcurrentThreadCount());

			while ((line = br.readLine()) != null)
			{

				// use comma as separator
				String[] data = line.split(cvsSplitBy);

				Runnable worker = new WorkerThread(data[0], data[1], ps, isLongKey, session);
				executor.execute(worker);

				totalcount++;

				if (totalcount % 1500 == 0)
				{
					double percent = totalcount / recordCount * 100;
					System.out.println(Math.round(percent) + " % Completed");
				}

				while (executor.getActiveCount() == getConcurrentThreadCount())
				{
					Thread.sleep(50);
				}

			}

			executor.shutdown();
			while (!executor.isTerminated())
			{
			}

			System.out.println("100 % Completed");

		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (InterruptedException e)
		{
			e.printStackTrace();
		} finally
		{
			session.getCluster().close();
			session.close();

			try
			{

				if (br != null)
					br.close();

			} catch (IOException ex)
			{

				ex.printStackTrace();

			}
		}

	}

	private double countLines(String filename)
	{
		BufferedReader reader = null;
		double lines = 0;
		try
		{
			reader = new BufferedReader(new FileReader(filename));
			while (reader.readLine() != null)
				lines++;
			reader.close();

		} catch (IOException e)
		{
			e.printStackTrace();
		} finally
		{
			try
			{
				if (reader != null)
					reader.close();
			} catch (IOException e)
			{
				e.printStackTrace();
			}
		}

		return lines;
	}

	public synchronized void appendToFile(String data)
	{
		BufferedWriter bw = null;
		FileWriter fw = null;

		try
		{

			File file = new File(getOutputCsvFile());

			// if file doesnt exists, then create it
			if (!file.exists())
			{
				file.createNewFile();
			}

			// true = append file
			fw = new FileWriter(file.getAbsoluteFile(), true);
			bw = new BufferedWriter(fw);

			bw.write(data);

		} catch (IOException e)
		{

			e.printStackTrace();

		} finally
		{

			try
			{

				if (bw != null)
					bw.close();

				if (fw != null)
					fw.close();

			} catch (IOException ex)
			{

				ex.printStackTrace();

			}
		}

	}

	public class WorkerThread implements Runnable {

		private String sid;
		private String keyId;
		private PreparedStatement ps;
		private boolean isLongKey;
		private Session session;

		public WorkerThread(String sid, String keyId, PreparedStatement ps, boolean isLongKey, Session session)
		{
			super();
			this.sid = sid;
			this.keyId = keyId;
			this.ps = ps;
			this.isLongKey = isLongKey;
			this.session = session;
		}

		@Override
		public void run()
		{

			StringBuffer outputString = new StringBuffer("");

			long start = new Date().getTime();

			ResultSetFuture future = null;

			try
			{
				if (isLongKey)
					future = session.executeAsync(ps.bind(Integer.parseInt(sid), Long.parseLong(keyId)));
				else
					future = session.executeAsync(ps.bind(Integer.parseInt(sid), Integer.parseInt(keyId)));

				while (!future.isDone())
				{
					wait(50);
				}

				ResultSet results = future.getUninterruptibly();
				

				String deleted = "Y";

				outputString.append(sid + "," + keyId + "," + deleted + "\n");
				
				appendToFile(outputString.toString());

			} catch (NumberFormatException e)
			{
				System.out.println("Study_ID: " + sid + " |  key_ID: " + keyId);
				long end = new Date().getTime();
				System.out.println("Timeout After : " + (end - start) + " ms");
				outputString.append(sid + "," + keyId + "," + "E" + "\n");
				e.printStackTrace();
			} finally
			{

			}

		}

		private void wait(int timeInMilliSec)
		{
			try
			{
				Thread.sleep(timeInMilliSec);
			} catch (InterruptedException e)
			{
				e.printStackTrace();
			}
		}

	}

	public boolean isInteger(String str)
	{

		if (str == null || str.equals(""))
			return false;

		int size = str.length();

		for (int i = 0; i < size; i++)
		{
			if (!Character.isDigit(str.charAt(i)))
			{
				return false;
			}
		}

		return size > 0;
	}

	private boolean loadProperties()
	{

		InputStream is = null;
		Properties prop = null;

		try
		{
			CodeSource src = CassandraDeleteUtility.class.getProtectionDomain().getCodeSource();
			if (src != null)
			{
				URL url = new URL(src.getLocation(), "delete_config.properties");

				prop = new Properties();
				is = new FileInputStream(new File(url.getPath()));
				prop.load(is);

				setHost(prop.getProperty("cassandra.host.ip"));
				setUsername(prop.getProperty("cassandra.username"));
				setPassword(prop.getProperty("cassandra.password"));
				setKeyspace(prop.getProperty("cassandra.keyspace.name"));
				String tableName = prop.getProperty("cassandra.table.name");
				CassandraTable table = CassandraTable.getTableFromString(tableName);
				if (table == null)
				{
					throw new GfkMriException("Cassandra Table Name is not valid : " + tableName);
				}
				setTable(table);

				String filePath = prop.getProperty("input.csv.file.path");

				File inputFile = new File(filePath);

				if (!inputFile.exists())
				{
					throw new GfkMriException("Input CSV file does not exists : " + filePath);
				}

				setInputCsvFile(filePath);

				String outputFile = inputFile.getParent() + "/outputData_" + CassandraTable.getTableName(getTable()) + ".csv";
				setOutputCsvFile(outputFile);

				String threadCount = prop.getProperty("concurrent.thread.count");
				if (isInteger(threadCount))
				{
					setConcurrentThreadCount(Integer.parseInt(threadCount));
				}

				String driverTimeout = prop.getProperty("driver.read.timeout.millis");
				if (isInteger(driverTimeout))
				{
					setDriverReadTimeout(Integer.parseInt(driverTimeout));
				}

			} else
			{
				System.out.println("Failed to load properties file ...");
				return false;
			}
		} catch (MalformedURLException e)
		{
			e.printStackTrace();
		} catch (FileNotFoundException e)
		{
			e.printStackTrace();
		} catch (IOException e)
		{
			e.printStackTrace();
		} catch (GfkMriException e)
		{
			e.printStackTrace();
		}

		return true;

	}

	public String getInputCsvFile()
	{
		return inputCsvFile;
	}

	public void setInputCsvFile(String inputCsvFile)
	{
		this.inputCsvFile = inputCsvFile;
	}

	public String getOutputCsvFile()
	{
		return outputCsvFile;
	}

	public void setOutputCsvFile(String outputCsvFile)
	{
		this.outputCsvFile = outputCsvFile;
	}

	public CassandraTable getTable()
	{
		return table;
	}

	public void setTable(CassandraTable table)
	{
		this.table = table;
	}

	public String getHost()
	{
		return host;
	}

	public void setHost(String host)
	{
		this.host = host;
	}

	public String getKeyspace()
	{
		return keyspace;
	}

	public void setKeyspace(String keyspace)
	{
		this.keyspace = keyspace;
	}

	public String getUsername()
	{
		return username;
	}

	public void setUsername(String username)
	{
		this.username = username;
	}

	public String getPassword()
	{
		return password;
	}

	public void setPassword(String password)
	{
		this.password = password;
	}

	public Integer getDriverReadTimeout()
	{
		return driverReadTimeout;
	}

	public void setDriverReadTimeout(Integer driverReadTimeout)
	{
		this.driverReadTimeout = driverReadTimeout;
	}

	public Integer getConcurrentThreadCount()
	{
		return concurrentThreadCount;
	}

	public void setConcurrentThreadCount(Integer concurrentThreadCount)
	{
		this.concurrentThreadCount = concurrentThreadCount;
	}

	public static void main(String[] args)
	{

		try
		{

			CassandraDeleteUtility cassandraDeleteUtility = new CassandraDeleteUtility();
			cassandraDeleteUtility.loadProperties();

			CassandraService cassandraService = new CassandraService();

			long start = new Date().getTime();

			Session session = cassandraService.createSession(Sets.newHashSet(InetAddress.getByName(cassandraDeleteUtility.getHost())), cassandraDeleteUtility.getKeyspace(),
					cassandraDeleteUtility.getUsername(), cassandraDeleteUtility.getPassword(), cassandraDeleteUtility.getDriverReadTimeout());

			cassandraDeleteUtility.deleteRecords(session);

			long end = new Date().getTime();

			System.out.println("Completion Time : " + (end - start) / 1000 + " Sec");

		} catch (UnknownHostException e)
		{
			e.printStackTrace();
		}

	}

}
