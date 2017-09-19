package com.crayonbox.utilities;

import net.dv8tion.jda.core.utils.tuple.ImmutablePair;

import java.util.ArrayList;
import java.util.List;

public class AutoRoleModule {

    private List<ImmutablePair<String, List<String>>> qrPairList;

    public AutoRoleModule(List<ImmutablePair<String, List<String>>> questionResponsePairList) {
        qrPairList = questionResponsePairList;
    }

    public List<String> getQuestions() {
        ArrayList<String> questions = new ArrayList<>();
        qrPairList.stream().forEachOrdered(curr -> questions.add(curr.getLeft()));
        return questions;
    }

    public List<String> getResponses(int index) {
        return qrPairList.get(index).getRight();
    }
}
