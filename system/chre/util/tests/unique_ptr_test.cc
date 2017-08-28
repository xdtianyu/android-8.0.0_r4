#include "gtest/gtest.h"

#include "chre/util/unique_ptr.h"

using chre::UniquePtr;
using chre::MakeUnique;

struct Value {
  Value(int value) : value(value) {
    constructionCounter++;
  }

  ~Value() {
    constructionCounter--;
  }

  Value& operator=(Value&& other) {
    value = other.value;
    return *this;
  }

  int value;
  static int constructionCounter;
};

int Value::constructionCounter = 0;

TEST(UniquePtr, Construct) {
  UniquePtr<Value> myInt = MakeUnique<Value>(0xcafe);
  ASSERT_FALSE(myInt.isNull());
  EXPECT_EQ(myInt.get()->value, 0xcafe);
  EXPECT_EQ(myInt->value, 0xcafe);
  EXPECT_EQ((*myInt).value, 0xcafe);
  EXPECT_EQ(myInt[0].value, 0xcafe);
}

TEST(UniquePtr, MoveConstruct) {
  UniquePtr<Value> myInt = MakeUnique<Value>(0xcafe);
  ASSERT_FALSE(myInt.isNull());
  Value *value = myInt.get();

  UniquePtr<Value> moved(std::move(myInt));
  EXPECT_EQ(moved.get(), value);
  EXPECT_EQ(myInt.get(), nullptr);
}

TEST(UniquePtr, Move) {
  Value::constructionCounter = 0;

  {
    UniquePtr<Value> myInt = MakeUnique<Value>(0xcafe);
    ASSERT_FALSE(myInt.isNull());
    EXPECT_EQ(Value::constructionCounter, 1);

    UniquePtr<Value> myMovedInt = MakeUnique<Value>(0);
    ASSERT_FALSE(myMovedInt.isNull());
    EXPECT_EQ(Value::constructionCounter, 2);
    myMovedInt = std::move(myInt);
    ASSERT_FALSE(myMovedInt.isNull());
    ASSERT_TRUE(myInt.isNull());
    EXPECT_EQ(myMovedInt.get()->value, 0xcafe);
  }

  EXPECT_EQ(Value::constructionCounter, 0);
}

TEST(UniquePtr, Release) {
  Value::constructionCounter = 0;

  Value *value1, *value2;
  {
    UniquePtr<Value> myInt = MakeUnique<Value>(0xcafe);
    ASSERT_FALSE(myInt.isNull());
    EXPECT_EQ(Value::constructionCounter, 1);
    value1 = myInt.get();
    EXPECT_NE(value1, nullptr);
    value2 = myInt.release();
    EXPECT_EQ(value1, value2);
    EXPECT_EQ(myInt.get(), nullptr);
    EXPECT_TRUE(myInt.isNull());
  }

  EXPECT_EQ(Value::constructionCounter, 1);
  EXPECT_EQ(value2->value, 0xcafe);
  value2->~Value();
  chre::memoryFree(value2);
}
