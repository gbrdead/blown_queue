AM_CPPFLAGS = -I$(top_srcdir)/src


voidland_includedir = $(includedir)/voidland/blown_queue

dist_voidland_include_HEADERS = \
    src/voidland/blown_queue/MPMC_PortionQueue.hpp \
    src/voidland/blown_queue/BlownQueue.hpp \
    src/voidland/blown_queue/NonBlockingQueue.hpp
nodist_voidland_include_HEADERS = blown_queue_config.h


if BUILD_TESTS

check_PROGRAMS = blown_queue_tests

blown_queue_tests_CPPFLAGS = $(AM_CPPFLAGS) $(GTEST_CPPFLAGS)
blown_queue_tests_LDADD = $(GTEST_LIBS)

blown_queue_tests_SOURCES = src/test/BlownQueueTest.cpp

endif
