package com.example.mylearning.data;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.room.migration.Migration;

import com.example.mylearning.data.dao.QuizAttemptDao;
import com.example.mylearning.data.dao.QuizQuestionDao;
import com.example.mylearning.data.dao.TopicDao;
import com.example.mylearning.data.dao.UserDao;
import com.example.mylearning.data.entity.QuizAttempt;
import com.example.mylearning.data.entity.QuizQuestion;
import com.example.mylearning.data.entity.Topic;
import com.example.mylearning.data.entity.User;
import com.example.mylearning.data.entity.UserTopic;

@Database(
        entities = {User.class, Topic.class, UserTopic.class, QuizAttempt.class, QuizQuestion.class},
        version = 2,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase instance;

    // Migration 1→2: clear topics so unique index rebuild works cleanly
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            // Drop and recreate topics table with the unique index on name
            database.execSQL("DROP TABLE IF EXISTS topics");
            database.execSQL("CREATE TABLE IF NOT EXISTS `topics` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT, `category` TEXT)");
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_topics_name` ON `topics` (`name`)");
        }
    };

    // Singleton — one DB connection shared across the app
    public static AppDatabase getInstance(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    "mylearning.db"
                            )
                            .addMigrations(MIGRATION_1_2)
                            .build();
                }
            }
        }
        return instance;
    }

    public abstract UserDao userDao();
    public abstract TopicDao topicDao();
    public abstract QuizAttemptDao quizAttemptDao();
    public abstract QuizQuestionDao quizQuestionDao();
}