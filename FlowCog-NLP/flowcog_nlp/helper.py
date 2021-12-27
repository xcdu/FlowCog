#!/usr/bin/env python
# -*- coding: utf-8 -*-
import re
import logging
import pandas as pd
import numpy as np
import itertools
from collections import Counter


def split_camel_case(text):
  matches = re.finditer(
      ".+?(?:(?<=[a-z])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|$)", text)
  return " ".join([m.group(0) for m in matches])


def clean_element(column, unique_element=False):
  removal = re.compile(u"[^a-zA-Z0-9\u4e00-\u9fa5]")
  removed = removal.sub(" ", str(column).strip().lower()).split()
  if unique_element:
    return list(set(removed))
  else:
    return removed


def pad_rows(rows, padding_word="<PAD/>", forced_sequence_length=None):
  if forced_sequence_length is None:  # Train
    sequence_length = min(max(len(x) for x in rows), 2500)
  else:
    logging.critical('using the trained sequence length when predicting')
    sequence_length = forced_sequence_length
  logging.critical('sequence length is {}'.format(sequence_length))

  padded = []
  for i in range(len(rows)):
    row = rows[i]
    num_padding = sequence_length - len(row)

    if num_padding < 0:
      padded_row = row[0:sequence_length]
    else:
      padded_row = row + [padding_word] * num_padding
    padded.append(padded_row)
  return padded


def convert_row_to_list(series, flow_relative_columns_num=4):
  attr = []
  for i, index in enumerate(series.index):
    if i < flow_relative_columns_num:
      add = list(set(series[index]))
    else:
      add = list(series[index])
    if not add:
      attr += ["<EMPTY/>"]
    else:
      attr += add
    attr += ["<SEP/>"]
  return attr


def build_vocab(sentences):
  word_counts = Counter(itertools.chain(*sentences))
  vocabulary_inv = [word[0] for word in word_counts.most_common()]
  vocabulary = {word: index for index, word in enumerate(vocabulary_inv)}
  return vocabulary, vocabulary_inv


def init_embedding(vocabulary):
  embedding = {}
  for word in vocabulary:
    embedding[word] = np.random.uniform(-0.25, 0.25, 200)
  return embedding


def batch_iter(data, batch_size, num_epochs, shuffle=True):
  data = np.array(data)
  data_size = len(data)
  num_batches_per_epoch = int(data_size / batch_size) + 1

  for epoch in range(num_epochs):
    if shuffle:
      shuffle_indices = np.random.permutation(np.arange(data_size))
      shuffled_data = data[shuffle_indices]
    else:
      shuffled_data = data

    for batch_num in range(num_batches_per_epoch):
      start_index = batch_num * batch_size
      end_index = min((batch_num + 1) * batch_size, data_size)
      yield shuffled_data[start_index:end_index]


def load(data_input):
  df = pd.read_pickle(data_input)

  selected = ["label", "source", "sink", "source_with_method",
              "sink_with_method", "path", "path_with_method", "parameter",
              "relative_parameter", "view", "layout_context", "relative_layout",
              "all_layouts"]

  flow_columns = ["source", "sink", "source_with_method",
                  "sink_with_method", "path", "path_with_method",
                  "parameter", "relative_parameter"]

  unselected = list(set(df.columns) - set(selected))
  df = df.drop(unselected, axis=1)
  df = df.dropna(axis=0, how="any", subset=selected)
  df = df.reindex(np.random.permutation(df.index))

  labels = sorted(list(set(df[selected[0]].tolist())))
  num_labels = len(labels)
  one_hot = np.zeros((num_labels, num_labels), int)
  np.fill_diagonal(one_hot, 1)
  label_dict = dict(zip(labels, one_hot))

  for i, row in df.iterrows():
    for flow_column in flow_columns:
      row[flow_column] = split_camel_case(row[flow_column])

  for i, column in enumerate(selected[1:]):
    unique = False
    if i < len(flow_columns):
      unique = True
    df[column] = df[column].apply(clean_element, unique_element=unique)
  x_raw = df[selected[1:]].apply(convert_row_to_list,
                                 flow_relative_columns_num=len(flow_columns),
                                 axis=1).tolist()
  y_raw = df[selected[0]].apply(lambda y: label_dict[y]).tolist()

  x_raw = pad_rows(x_raw)

  vocabulary, vocabulary_inv = build_vocab(x_raw)

  x = np.array([[vocabulary[word] for word in sentence] for sentence in x_raw])
  y = np.array(y_raw)

  return x, y, vocabulary, vocabulary_inv, df, labels


if __name__ == "__main__":
  input_file = "data/gathered.en.1.pd"
  x_, y_, vocab_, vocab_inv_, df_, labels_ = load(input_file)
