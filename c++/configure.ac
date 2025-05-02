AC_INIT([blown_queue], [1.0.0], [gbr@voidland.org])
AM_INIT_AUTOMAKE([foreign subdir-objects])


BUILD_TESTS="yes"


AX_CXX_COMPILE_STDCXX([20], [noext], [optional])
if test "x${HAVE_CXX20}" = "x0"
then

    AC_MSG_WARN([C++20 not detected, the tests will not be built.])
    BUILD_TESTS="no"
    
    AX_CXX_COMPILE_STDCXX([17], [noext], [optional])
    if test "x${HAVE_CXX17}" = "x0"
    then
        AX_CXX_COMPILE_STDCXX([14], [noext], [optional])
        if test "x${HAVE_CXX14}" = "x0"
        then
            AX_CXX_COMPILE_STDCXX([11], [noext], [mandatory])
        fi
    fi
fi


AX_PTHREAD
if test "x${ax_pthread_ok}" = "xyes"
then
    LIBS="${PTHREAD_LIBS} ${LIBS}"
    CFLAGS="${CFLAGS} ${PTHREAD_CFLAGS}"
    CXXFLAGS="${CXXFLAGS} ${PTHREAD_CFLAGS}"
else
    AC_MSG_ERROR([POSIX threads are required])
fi


AC_LANG_PUSH([C++])


rm -f blown_queue_config.h
touch blown_queue_config.h


AC_MSG_CHECKING([for atomic_queue library])
AC_LINK_IFELSE(
    [AC_LANG_PROGRAM(
        [[
            #include <atomic_queue/atomic_queue.h>
            #include <string>
            #include <utility>
        ]],
        [[
            atomic_queue::AtomicQueueB2<std::string> queue(1);
            std::string s;
            queue.push(std::move(s));
            queue.try_pop(s);
        ]])],
    [
        ATOMIC_QUEUE="yes"
        echo "#define HAVE_ATOMIC_QUEUE" >> blown_queue_config.h
        AC_MSG_RESULT([yes])
    ],
    [
        ATOMIC_QUEUE="no"
        AC_MSG_RESULT([no])
        AC_MSG_NOTICE([Get atomic_queue from https://max0x7ba.github.io/atomic_queue/ or install it using your package manager (the Debian package name is libatomic-queue-dev).])        
    ])


AC_MSG_CHECKING([for moodycamel::ConcurrentQueue library])
AC_LINK_IFELSE(
    [AC_LANG_PROGRAM(
        [[
            #include <concurrentqueue/moodycamel/concurrentqueue.h>
            #include <string>
            #include <utility>
        ]],
        [[
            moodycamel::ConcurrentQueue<std::string> queue(1);
            std::string s;
            queue.enqueue(std::move(s));
            queue.try_dequeue(s);
        ]])],
    [
        MOODYCAMEL_CONCURRENT_QUEUE="yes"
        echo "#define HAVE_MOODYCAMEL_CONCURRENT_QUEUE" >> blown_queue_config.h
        echo "#define HAVE_MOODYCAMEL_IN_INCLUDE_PATH" >> blown_queue_config.h
        AC_MSG_RESULT([yes])
    ],
    [
        AC_LINK_IFELSE(
            [AC_LANG_PROGRAM(
                [[
                    #include <concurrentqueue/concurrentqueue.h>
                    #include <string>
                    #include <utility>
                ]],
                [[
                    moodycamel::ConcurrentQueue<std::string> queue(1);
                    std::string s;
                    queue.enqueue(std::move(s));
                    queue.try_dequeue(s);
                ]])],
            [
                MOODYCAMEL_CONCURRENT_QUEUE="yes"
                echo "#define HAVE_MOODYCAMEL_CONCURRENT_QUEUE" >> blown_queue_config.h
                AC_MSG_RESULT([yes])
            ],
            [
                MOODYCAMEL_CONCURRENT_QUEUE="no"
                AC_MSG_RESULT([no])
                AC_MSG_NOTICE([Get moodycamel::ConcurrentQueue from https://github.com/cameron314/concurrentqueue or install it using your package manager (the Debian package name is libconcurrentqueue-dev).])
            ])
    ])


AC_MSG_CHECKING([for Boost.Lockfree library])
AC_LINK_IFELSE(
    [AC_LANG_PROGRAM(
        [[
            #include <boost/lockfree/queue.hpp>
        ]],
        [[
            boost::lockfree::queue<int*> queue(1);
            int* p = nullptr;
            queue.bounded_push(p);
            queue.pop(p);
        ]])],
    [
        BOOST_LOCKFREE="yes"
        echo "#define HAVE_BOOST_LOCKFREE" >> blown_queue_config.h
        AC_MSG_RESULT([yes])
    ],
    [
        BOOST_LOCKFREE="no"
        AC_MSG_RESULT([no])
        AC_MSG_NOTICE([Get boost from https://www.boost.org/ or install it using your package manager (the Debian package name is libboost-dev).])
    ])
    
    
AC_MSG_CHECKING([for xenium library])
AC_LINK_IFELSE(
    [AC_LANG_PROGRAM(
        [[
            #include <xenium/reclamation/generic_epoch_based.hpp>
            #include <xenium/michael_scott_queue.hpp>
            #include <xenium/ramalhete_queue.hpp>
            #include <xenium/vyukov_bounded_queue.hpp>
            
            #include <string>
            #include <utility>
        ]],
        [[
            std::string s;
            int* p = nullptr;
        
            xenium::michael_scott_queue<std::string, xenium::policy::reclaimer<xenium::reclamation::epoch_based<>>> queue1;
            queue1.push(std::move(s));
            queue1.try_pop(s);
            
            xenium::ramalhete_queue<int*, xenium::policy::reclaimer<xenium::reclamation::epoch_based<>>> queue2;
            queue2.push(p);
            queue2.try_pop(p);
            
            xenium::vyukov_bounded_queue<std::string> queue3(1);
            queue3.try_push(std::move(s));
            queue3.try_pop(s);
        ]])],
    [
        XENIUM="yes"
        echo "#define HAVE_XENIUM" >> blown_queue_config.h
        AC_MSG_RESULT([yes])
    ],
    [
        XENIUM="no"
        AC_MSG_RESULT([no])
        AC_MSG_NOTICE([Get xenium from https://mpoeter.github.io/xenium/ or install it using your package manager (the Debian package name is libxenium-dev).])
    ])



if test "x${ATOMIC_QUEUE}" != "xyes" && \
   test "x${MOODYCAMEL_CONCURRENT_QUEUE}" != "xyes" && \
   test "x${BOOST_LOCKFREE}" != "xyes" && \
   test "x${XENIUM}" != "xyes"
then
    AC_MSG_WARN([No supported lock-free queue detected.])
    BUILD_TESTS="no"
fi



if test "x${BUILD_TESTS}" != "xno"
then
    GTEST_CPPFLAGS="-DGTEST_HAS_PTHREAD=1"
    GTEST_LIBS="-lgtest_main -lgtest"

    save_CPPFLAGS="${CPPFLAGS}"
    save_LIBS="${LIBS}"

    CPPFLAGS="${GTEST_CPPFLAGS} ${CPPFLAGS}"
    LIBS="${GTEST_LIBS} ${LIBS}"

    AC_MSG_CHECKING([for GoogleTest library])
    AC_LINK_IFELSE(
        [AC_LANG_SOURCE(
            [[
                #include <gtest/gtest.h>
            
                class ATest :
                    public testing::Test
                {
                };
            
                TEST_F(ATest, aCase)
                {
                }
            ]])],
        [
            GTEST="yes"
            AC_MSG_RESULT([yes])
        ],
        [
            GTEST="no"
            AC_MSG_RESULT([no])
            AC_MSG_NOTICE([Get GoogleTest from https://github.com/google/googletest or install it using your package manager (the Debian package name is libgtest-dev).])
            AC_MSG_WARN([GoogleTest not detected, the tests will not be built.])
            BUILD_TESTS="no"
        ])
    
    CPPFLAGS="${save_CPPFLAGS}"
    LIBS="${save_LIBS}"
fi
    

AC_LANG_POP([C++])


AC_SUBST([GTEST_CPPFLAGS])
AC_SUBST([GTEST_LIBS])
AM_CONDITIONAL(BUILD_TESTS, test "x${BUILD_TESTS}" = "xyes")

AC_CONFIG_FILES([Makefile])
AC_OUTPUT
