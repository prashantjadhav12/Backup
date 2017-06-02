package com.gfk.mri.constants;

public enum CassandraTable {

	RESPONSES("responses"), 
	QUESTION_RESPONDENTS("question_respondents"), 
	RESPONDENT_WEIGHT("respondent_weight");

	private String tableName;

	private CassandraTable(String tableName)
	{
		this.tableName = tableName;
	}

	public static CassandraTable getTableFromString(String tableName)
	{
		for (CassandraTable table : CassandraTable.values())
		{
			if (table.tableName.equalsIgnoreCase(tableName))
				return table;
		}

		return null;
	}
	
	public static String getTableName(CassandraTable table)
	{
		return table.tableName;
	}

}
