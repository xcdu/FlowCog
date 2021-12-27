import os
import sys
import json
import shutil
import pickle
import logging
import numpy as np
import pandas as pd
import tensorflow as tf
from text_cnn_rnn import TextCNNRNN
import re
import helper

logging.getLogger().setLevel(logging.INFO)


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


def load_trained_params(trained_dir):
  params = json.loads(
    open(trained_dir + 'trained_parameters.json', encoding="utf-8").read())
  words_index = json.loads(
    open(trained_dir + 'words_index.json', encoding="utf-8").read())
  labels = json.loads(
    open(trained_dir + 'labels.json', encoding="utf-8").read())

  with open(trained_dir + 'embeddings.pickle', 'rb') as input_file:
    fetched_embedding = pickle.load(input_file)
  embedding_mat = np.array(fetched_embedding, dtype=np.float32)
  return params, words_index, labels, embedding_mat


def load_test_data(test_file_input, labels):
  df = pd.read_pickle(test_file_input)
  selected = ["source", "sink", "source_with_method",
              "sink_with_method", "path", "path_with_method", "parameter",
              "relative_parameter", "view", "layout_context", "relative_layout",
              "all_layouts"]

  flow_columns = ["source", "sink", "source_with_method",
                  "sink_with_method", "path", "path_with_method",
                  "parameter", "relative_parameter"]

  unselected = list(set(df.columns) - set(selected))
  df = df.dropna(axis=0, how="any", subset=selected)

  for i, row in df.iterrows():
    for flow_column in flow_columns:
      row[flow_column] = split_camel_case(row[flow_column])
    if "app_name" in df.columns:
      row["app_name"] = "".join(row["app_name"].split(" "))

  for i, column in enumerate(selected):
    unique = False
    if i < len(flow_columns):
      unique = True
    df[column] = df[column].apply(clean_element, unique_element=unique)
  test_set = df.drop(unselected, axis=1).apply(
      convert_row_to_list, flow_relative_columns_num=len(flow_columns),
      axis=1).tolist()

  # labels = sorted(list(set(labels)))
  num_labels = len(labels)
  one_hot = np.zeros((num_labels, num_labels), int)
  np.fill_diagonal(one_hot, 1)
  label_dict = dict(zip(labels, one_hot))

  y_ = None
  if "label" in df.columns:
    selected.append("label")
    y_ = df[selected[len(selected) - 1]].apply(
        lambda x: label_dict[x] if x in label_dict else None).tolist()

  return test_set, y_, df


def map_word_to_index(examples, words_index):
  x_ = []
  for i, example in enumerate(examples):
    temp = []
    for word in example:
      if word in words_index:
        temp.append(words_index[word])
      else:
        temp.append(0)
    x_.append(temp)
  return np.array([[v for v in tmp] for tmp in x_])


def predict_unseen_data():
  trained_dir = sys.argv[1]
  if not trained_dir.endswith('/'):
    trained_dir += '/'
  test_file = sys.argv[2]

  params, words_index, labels, embedding_mat = load_trained_params(trained_dir)
  x_, y_, df = load_test_data(test_file, labels)

  x_ = helper.pad_rows(x_, forced_sequence_length=params['sequence_length'])

  x_ = map_word_to_index(x_, words_index)

  x_test, y_test = np.asarray(x_), None
  has_none = False
  if y_ is not None:
    for i in y_:
      if i is None:
        has_none = True
        break
    if not has_none:
      y_test = np.asarray(y_)

  timestamp = trained_dir.split('/')[-2].split('_')[-1]
  predicted_dir = 'predicts/predicted_results_' + timestamp + '/'
  if os.path.exists(predicted_dir):
    shutil.rmtree(predicted_dir)
  os.makedirs(predicted_dir)

  with tf.Graph().as_default():
    session_conf = tf.ConfigProto(allow_soft_placement=True,
                                  log_device_placement=False)
    sess = tf.Session(config=session_conf)
    with sess.as_default():
      cnn_rnn = TextCNNRNN(
          embedding_mat=embedding_mat,
          non_static=params['non_static'],
          hidden_unit=params['hidden_unit'],
          sequence_length=len(x_test[0]),
          max_pool_size=params['max_pool_size'],
          filter_sizes=map(int, params['filter_sizes'].split(",")),
          num_filters=params['num_filters'],
          num_classes=len(labels),
          embedding_size=params['embedding_dim'],
          l2_reg_lambda=params['l2_reg_lambda'])

      def real_len(batches):
        return [np.ceil(np.argmin(batch + [0]) * 1.0 / params['max_pool_size'])
                for batch in batches]

      def predict_step(x_batch):
        feed_dict = {
          cnn_rnn.input_x: x_batch,
          cnn_rnn.dropout_keep_prob: 1.0,
          cnn_rnn.batch_size: len(x_batch),
          cnn_rnn.pad: np.zeros([len(x_batch), 1, params['embedding_dim'], 1]),
          cnn_rnn.real_len: real_len(x_batch),
        }
        predictions = sess.run([cnn_rnn.predictions], feed_dict)
        return predictions

      checkpoint_file = trained_dir + 'best_model.ckpt'
      saver = tf.train.Saver(tf.all_variables())
      saver.restore(sess, checkpoint_file)
      logging.critical('{} has been loaded'.format(checkpoint_file))

      batches = helper.batch_iter(list(x_test), params['batch_size'], 1,
                                  shuffle=False)

      predictions, predict_labels = [], []
      for x_batch in batches:
        batch_predictions = predict_step(x_batch)[0]
        # print(batch_predictions)
        for batch_prediction in batch_predictions:
          predictions.append(batch_prediction)
          predict_labels.append(labels[batch_prediction])

      # Save the predictions back to file
      df["predicted"] = predict_labels

      # columns = sorted(df.columns, reverse=True)
      columns = ["predicted"] + [column for column in df.columns if
                                 str(column) is not "predicted"]
      df.to_csv(predicted_dir + 'predictions_all.csv', index=False,
                columns=columns)

      if y_test is not None:
        y_test = np.array(np.argmax(y_test, axis=1))
        accuracy = sum(np.array(predictions) == y_test) / float(len(y_test))
        logging.critical('The prediction accuracy is: {}'.format(accuracy))

      logging.critical(
          'Prediction is complete, all files have been saved: {}'.format(
              predicted_dir))


if __name__ == '__main__':
  # python predict.py models/trained_results data/random.pd
  predict_unseen_data()
