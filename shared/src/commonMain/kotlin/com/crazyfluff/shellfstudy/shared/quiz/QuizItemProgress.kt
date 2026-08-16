package com.crazyfluff.shellfstudy.shared.quiz

class QuizItemProgress<T>(val item: T) {
    var meaningDone = false
    var readingDone = false
    var hadIncorrectMeaning = false
    var hadIncorrectReading = false
    val hasAnyProgress: Boolean get() = meaningDone || readingDone || hadIncorrectMeaning || hadIncorrectReading
}
