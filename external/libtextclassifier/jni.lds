{
  # Export symbols that correspond to our JNIEXPORTed functions.
  global:
    Java_android_view_textclassifier_*;

  # Hide everything else.
  local:
    *;
};
