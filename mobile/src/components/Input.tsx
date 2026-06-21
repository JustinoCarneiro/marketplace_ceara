import React, { useState } from 'react';
import { View, TextInput, Text, StyleSheet, TouchableOpacity, TextInputProps } from 'react-native';
import { color, font, radius, space } from '../theme';

interface Props extends TextInputProps {
  label?: string;
  error?: string;
  hint?: string;
  secure?: boolean;
}

export default function Input({ label, error, hint, secure, style, ...rest }: Props) {
  const [show, setShow] = useState(false);
  const hasError = !!error;

  return (
    <View style={styles.wrapper}>
      {label && <Text style={styles.label}>{label}</Text>}
      <View style={[styles.row, hasError && styles.rowError]}>
        <TextInput
          style={[styles.input, style]}
          placeholderTextColor={color.textFaint}
          secureTextEntry={secure && !show}
          autoCapitalize="none"
          {...rest}
        />
        {secure && (
          <TouchableOpacity onPress={() => setShow(v => !v)} style={styles.eye} hitSlop={8}>
            <Text style={styles.eyeIcon}>{show ? '🙈' : '👁️'}</Text>
          </TouchableOpacity>
        )}
      </View>
      {(hint || error) && (
        <Text style={[styles.hint, hasError && styles.hintError]}>{error ?? hint}</Text>
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: { gap: space[1] },
  label: {
    fontSize: font.size.eyebrow,
    fontWeight: font.weight.semibold,
    color: color.textSoft,
    letterSpacing: 0.2,
    textTransform: 'uppercase',
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    height: 52,
    borderRadius: radius.field,
    borderWidth: 1.5,
    borderColor: color.line,
    backgroundColor: color.surface,
    paddingHorizontal: space[4],
  },
  rowError: { borderColor: color.danger },
  input: {
    flex: 1,
    fontSize: font.size.body,
    color: color.text,
    fontFamily: font.family,
  },
  eye: { paddingLeft: space[2] },
  eyeIcon: { fontSize: 18 },
  hint: { fontSize: font.size.caption, color: color.textFaint },
  hintError: { color: color.danger },
});
