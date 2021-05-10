package com.example.rumb_test

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DBHandler(context: Context) : SQLiteOpenHelper(context, "config.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""create table configs
(
	saveName text not null
		constraint configs_pk
			primary key,
	isFromGeodez integer not null,
	isToGeodez integer not null,
	fromWgs integer not null,
	toWgs integer,
	inputFrom1 text not null,
	inputFrom2 text not null,
	inputFrom3 text not null
);""")
        db.execSQL("""create unique index configs_saveName_uindex
	on configs (saveName);""")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        onUpgrade(db, oldVersion, newVersion)
    }



}
