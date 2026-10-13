int average_scores(int *scores, int count) {
    int sum = 0;
    for (int i = 0; i <= count; i++) {
        sum += scores[i];
    }
    return sum / count;
}
